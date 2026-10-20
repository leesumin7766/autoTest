package com.autotest.test_management_service.application.testcase;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionJobRepository;
import com.autotest.test_management_service.application.service.DocumentDigest;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.autotest.test_management_service.domain.submission.SubmissionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class MenuTreeService {
    private static final String EXTERNAL_SCHEMA = "ui-menu-tree-v1";
    private static final Pattern NODE_ID = Pattern.compile("ui-[A-Za-z0-9_-]{1,60}");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SubmissionPersistenceService submissions;
    private final SubmissionRepository repository;
    private final ProductDescriptionJobRepository verdicts;
    private final TransactionTemplate transactions;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final String baseUrl;
    private final String internalToken;

    public MenuTreeService(JdbcTemplate jdbc, ObjectMapper mapper, SubmissionPersistenceService submissions,
            SubmissionRepository repository, ProductDescriptionJobRepository verdicts,
            PlatformTransactionManager transactionManager,
            @Value("${ai-service.base-url:http://localhost:8005}") String baseUrl,
            @Value("${product-description.internal-token:}") String internalToken) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.submissions = submissions;
        this.repository = repository;
        this.verdicts = verdicts;
        this.transactions = new TransactionTemplate(transactionManager);
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.internalToken = internalToken;
    }

    private ProductDescriptionException error(HttpStatus status, String code, String message) {
        return new ProductDescriptionException(status, code, message);
    }

    private List<com.autotest.test_management_service.domain.submission.SubmittedDocument> readyDocuments(UUID id, long memberId) {
        var submission = submissions.findById(SubmissionId.of(id))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "문서 세트를 찾을 수 없습니다."));
        if (!submission.memberId().value().equals(memberId))
            throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 문서 세트에 접근할 수 없습니다.");
        var docs = submissions.deliveryDocuments(submissions.findDocumentsBySubmissionId(SubmissionId.of(id)));
        var roles = docs.stream().map(d -> d.role()).collect(java.util.stream.Collectors.toSet());
        if (!roles.containsAll(Set.of(com.autotest.test_management_service.domain.submission.SubmissionType.FUNCTION_LIST,
                    com.autotest.test_management_service.domain.submission.SubmissionType.MANUAL))
                || docs.size() < 2 || docs.size() > 3 || roles.size() != docs.size()
                || docs.stream().anyMatch(d -> d.status() != SubmissionStatus.PARSED || d.extractedText() == null || d.extractedText().isBlank()))
            throw error(HttpStatus.CONFLICT, "DOCUMENTS_NOT_READY", "기능리스트와 매뉴얼이 정상 파싱되어야 합니다.");
        var verdict = verdicts.findDeliveryVerdict(id).orElse(null);
        if (verdict == null || !"DELIVERED".equals(verdict.status()))
            throw error(HttpStatus.CONFLICT, "PREFLIGHT_NOT_VERIFIED", "문서 검증이 완료되지 않았습니다.");
        try {
            JsonNode preflight = mapper.readTree(verdict.preflightJson() == null ? "{}" : verdict.preflightJson());
            if (!Set.of("READY", "READY_WITH_WARNINGS").contains(preflight.path("decision").asText())
                    || (preflight.path("generationGate").has("allowed") && !preflight.path("generationGate").path("allowed").asBoolean()))
                throw error(HttpStatus.CONFLICT, "PREFLIGHT_BLOCKED", "문서 검증에서 생성을 차단했습니다.");
            if (!DocumentDigest.of(docs).equals(verdict.documentDigest()))
                throw error(HttpStatus.CONFLICT, "DOCUMENT_VERSION_MISMATCH", "현재 문서를 다시 검증해야 합니다.");
        } catch (ProductDescriptionException known) { throw known;
        } catch (Exception failure) { throw error(HttpStatus.CONFLICT, "PREFLIGHT_NOT_VERIFIED", "문서 검증 기록을 확인할 수 없습니다."); }
        return docs;
    }

    public JsonNode status(UUID id, long memberId) {
        authorize(id, memberId);
        var docs = submissions.deliveryDocuments(submissions.findDocumentsBySubmissionId(SubmissionId.of(id)));
        var roles = docs.stream().map(d -> d.role()).collect(java.util.stream.Collectors.toSet());
        boolean complete = roles.containsAll(Set.of(com.autotest.test_management_service.domain.submission.SubmissionType.FUNCTION_LIST,
                    com.autotest.test_management_service.domain.submission.SubmissionType.MANUAL))
                && docs.size() >= 2 && docs.size() <= 3 && roles.size() == docs.size()
                && docs.stream().allMatch(d -> d.status() == SubmissionStatus.PARSED && d.extractedText() != null && !d.extractedText().isBlank());
        if (!complete) { ObjectNode response = mapper.createObjectNode(); response.putNull("menuTree"); return response; }
        String digest = DocumentDigest.of(docs);
        var rows = jdbc.query("SELECT member_id, manual_file_id, document_digest, tree_status, tree_json FROM menu_tree_approvals WHERE submission_id=?",
                (rs, n) -> {
                    ObjectNode value = mapper.createObjectNode();
                    value.put("memberId", rs.getLong("member_id"));
                    value.put("manualFileId", rs.getString("manual_file_id"));
                    value.put("documentDigest", rs.getString("document_digest").trim());
                    value.put("status", rs.getString("tree_status"));
                    value.set("tree", parseStoredTree(rs.getString("tree_json")));
                    return value;
                }, id);
        ObjectNode response = mapper.createObjectNode();
        if (rows.isEmpty()) { response.putNull("menuTree"); return response; }
        ObjectNode stored = rows.get(0);
        stored.put("stale", !digest.equals(stored.path("documentDigest").asText())
                || !EXTERNAL_SCHEMA.equals(stored.path("tree").path("schemaVersion").asText()));
        if (stored.path("stale").asBoolean()) stored.put("status", "STALE");
        stored.remove(List.of("memberId", "documentDigest"));
        response.set("menuTree", stored);
        return response;
    }

    public JsonNode externalPrompt(UUID id, long memberId, String sourceType, String sourceText) {
        readyDocuments(id, memberId);
        validateSource(sourceType, sourceText);
        ObjectNode requestBody = mapper.createObjectNode().put("sourceType", sourceType)
                .put("sourceText", sourceText == null ? "" : sourceText);
        return postAi("prompt-preview", requestBody);
    }

    public JsonNode importExternalDraft(UUID id, long memberId, String sourceType, String sourceText, JsonNode proposedTree) {
        validateSource(sourceType, sourceText);
        String evidenceSource = sourceText == null ? "" : sourceText;
        if (proposedTree == null || !EXTERNAL_SCHEMA.equals(proposedTree.path("schemaVersion").asText())
                || !proposedTree.path("nodes").isArray())
            throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_INVALID", "외부 LLM 응답은 ui-menu-tree-v1 JSON 형식이어야 합니다.");
        var docs = readyDocuments(id, memberId);
        if (!sourceType.equals(proposedTree.path("sourceType").asText(sourceType)))
            throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_SOURCE_MISMATCH", "프롬프트와 JSON 응답의 화면 자료 유형이 다릅니다.");
        Set<String> seenIds = new HashSet<>();
        ArrayNode nodes = mapper.createArrayNode();
        normalizeUi(proposedTree.path("nodes"), seenIds, new java.util.ArrayList<>(), nodes, 1, sourceType, evidenceSource);
        if (nodes.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_EMPTY", "최소 한 개의 근거 있는 기능을 포함해야 합니다.");
        ObjectNode draft = mapper.createObjectNode().put("schemaVersion", EXTERNAL_SCHEMA)
                .put("submissionId", id.toString()).put("sourceType", sourceType)
                .put("status", "DRAFT_NEEDS_REVIEW");
        draft.set("nodes", nodes);
        transactions.execute(tx -> {
            repository.findByIdForUpdate(SubmissionId.of(id)).orElseThrow();
            var currentDocs = readyDocuments(id, memberId);
            if (!DocumentDigest.of(currentDocs).equals(DocumentDigest.of(docs)))
                throw error(HttpStatus.CONFLICT, "DOCUMENT_VERSION_MISMATCH", "메뉴 트리 작성 중 문서가 변경되었습니다.");
            String manualFileId = docs.stream().filter(d -> "MANUAL".equals(d.role().name())).findFirst()
                    .map(d -> d.fileId().toString()).orElse("");
            draft.put("manualFileId", manualFileId);
            jdbc.update("""
                    INSERT INTO menu_tree_approvals(submission_id, member_id, manual_file_id, document_digest, tree_status, tree_json)
                    VALUES (?, ?, ?, ?, 'DRAFT_NEEDS_REVIEW', ?::jsonb)
                    ON CONFLICT (submission_id) DO UPDATE SET member_id=EXCLUDED.member_id, manual_file_id=EXCLUDED.manual_file_id,
                    document_digest=EXCLUDED.document_digest, tree_status='DRAFT_NEEDS_REVIEW', tree_json=EXCLUDED.tree_json, updated_at=now()
                    """, id, memberId, UUID.fromString(manualFileId), DocumentDigest.of(docs), draft.toString());
            return null;
        });
        return status(id, memberId);
    }

    private void validateSource(String sourceType, String sourceText) {
        if (sourceType == null || !Set.of("DOM", "SCREENSHOT").contains(sourceType))
            throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_SOURCE_INVALID", "화면 자료 유형을 선택해야 합니다.");
        if (sourceText != null && sourceText.length() > 500_000)
            throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_SOURCE_TOO_LARGE", "DOM 자료가 너무 깁니다. 메뉴와 관련된 Elements만 붙여 넣어 주세요.");
        if ("DOM".equals(sourceType) && (sourceText == null || sourceText.isBlank()))
            throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_SOURCE_REQUIRED", "F12 Elements 내용을 붙여 넣어 주세요.");
        if ("SCREENSHOT".equals(sourceType) && sourceText != null && !sourceText.isBlank())
            throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_SOURCE_INVALID", "스크린샷 방식에서는 DOM 텍스트를 비워 주세요.");
    }

    private JsonNode postAi(String path, ObjectNode body) {
        if (internalToken == null || internalToken.isBlank())
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "INTERNAL_AUTH_NOT_CONFIGURED", "생성 서비스 인증 설정이 없습니다.");
        try {
            var request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/menu-trees/" + path))
                    .timeout(Duration.ofMinutes(5)).header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2)
                throw error(HttpStatus.BAD_GATEWAY, "MENU_TREE_PROMPT_FAILED", "화면 자료용 메뉴 트리 프롬프트를 만들지 못했습니다.");
            return mapper.readTree(response.body());
        } catch (ProductDescriptionException known) { throw known;
        } catch (Exception failure) {
            throw error(HttpStatus.BAD_GATEWAY, "MENU_TREE_PROMPT_FAILED", "프롬프트 서비스에 연결하지 못했습니다.");
        }
    }

    public JsonNode approve(UUID id, long memberId, JsonNode proposedTree) {
        if (proposedTree == null)
            throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_INVALID", "메뉴 트리를 전달해야 합니다.");
        return transactions.execute(tx -> {
            repository.findByIdForUpdate(SubmissionId.of(id))
                    .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "문서 세트를 찾을 수 없습니다."));
            var docs = readyDocuments(id, memberId);
            String digest = DocumentDigest.of(docs);
            var rows = jdbc.query("SELECT member_id, manual_file_id, document_digest, tree_json FROM menu_tree_approvals WHERE submission_id=?",
                    (rs, n) -> new StoredTree(rs.getLong("member_id"), rs.getString("manual_file_id"),
                            rs.getString("document_digest").trim(), parseStoredTree(rs.getString("tree_json"))), id);
            if (rows.isEmpty()) throw error(HttpStatus.CONFLICT, "MENU_TREE_DRAFT_REQUIRED", "먼저 메뉴 트리 초안을 생성해야 합니다.");
            StoredTree stored = rows.get(0);
            if (!stored.digest().equals(digest)) throw error(HttpStatus.CONFLICT, "MENU_TREE_STALE", "문서가 변경되어 메뉴 트리를 다시 생성해야 합니다.");
            if (!EXTERNAL_SCHEMA.equals(stored.tree().path("schemaVersion").asText()))
                throw error(HttpStatus.CONFLICT, "MENU_TREE_EXTERNAL_DRAFT_REQUIRED", "외부 LLM 응답으로 메뉴 트리 초안을 만든 뒤 승인해야 합니다.");
            JsonNode submittedNodes = proposedTree.path("nodes");
            if (!submittedNodes.isArray()) throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_INVALID", "메뉴 트리 형식이 올바르지 않습니다.");
            Map<String, JsonNode> sourceNodes = new HashMap<>();
            collect(stored.tree().path("nodes"), sourceNodes);
            Set<String> seen = new HashSet<>();
            ArrayNode approvedNodes = mapper.createArrayNode();
            normalizeApproved(submittedNodes, sourceNodes, seen, new java.util.ArrayList<>(), approvedNodes, 1);
            if (approvedNodes.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_EMPTY", "최소 한 개의 기능을 포함해야 합니다.");
            ObjectNode tree = mapper.createObjectNode().put("schemaVersion", EXTERNAL_SCHEMA)
                    .put("submissionId", id.toString()).put("manualFileId", stored.manualFileId())
                    .put("sourceType", stored.tree().path("sourceType").asText()).put("status", "APPROVED");
            tree.set("nodes", approvedNodes);
            tree.put("approvedMemberId", memberId);
            jdbc.update("UPDATE menu_tree_approvals SET tree_status='APPROVED', tree_json=?::jsonb, updated_at=now() WHERE submission_id=?",
                    tree.toString(), id);
            return status(id, memberId);
        });
    }

    private record StoredTree(long memberId, String manualFileId, String digest, JsonNode tree) { }

    private JsonNode parseStoredTree(String value) {
        try { return mapper.readTree(value); }
        catch (Exception invalid) { throw new IllegalStateException("MENU_TREE_STORAGE_INVALID", invalid); }
    }

    private void collect(JsonNode nodes, Map<String, JsonNode> result) {
        for (JsonNode node : nodes) {
            String nodeId = node.path("nodeId").asText();
            if (!nodeId.isBlank()) result.put(nodeId, node);
            collect(node.path("children"), result);
        }
    }

    private void normalizeUi(JsonNode nodes, Set<String> seen, List<String> parentPath,
            ArrayNode output, int depth, String sourceType, String sourceText) {
        if (depth > 5 && !nodes.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_DEPTH_EXCEEDED", "메뉴 트리는 최대 5 depth까지 허용됩니다.");
        for (JsonNode proposed : nodes) {
            String nodeId = proposed.path("nodeId").asText("").trim();
            String title = proposed.path("title").asText("").trim();
            JsonNode evidence = proposed.path("evidence");
            if (!NODE_ID.matcher(nodeId).matches() || !seen.add(nodeId) || title.isBlank() || title.length() > 120
                    || !evidence.isArray() || evidence.isEmpty() || evidence.size() > 8)
                throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_INVALID", "메뉴 ID, 이름 또는 화면 근거가 올바르지 않습니다.");
            ArrayNode verifiedEvidence = mapper.createArrayNode();
            for (JsonNode item : evidence) {
                String quote = item.asText("").trim();
                if (quote.isBlank() || quote.length() > 500
                        || ("DOM".equals(sourceType) && !sourceText.contains(quote)))
                    throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_EVIDENCE_INVALID", "DOM 메뉴 근거가 입력한 Elements 내용과 일치하지 않습니다.");
                verifiedEvidence.add(quote);
            }
            ObjectNode node = mapper.createObjectNode().put("nodeId", nodeId).put("title", title)
                    .put("classification", "UI_MENU").put("menuDepth", depth).put("reviewStatus", "NEEDS_HUMAN_REVIEW");
            node.set("evidence", verifiedEvidence);
            ArrayNode featurePath = mapper.createArrayNode();
            parentPath.forEach(featurePath::add); featurePath.add(title);
            node.set("featurePath", featurePath);
            ArrayNode children = mapper.createArrayNode();
            var nextPath = new java.util.ArrayList<>(parentPath); nextPath.add(title);
            normalizeUi(proposed.path("children"), seen, nextPath, children, depth + 1, sourceType, sourceText);
            node.set("children", children); output.add(node);
        }
    }

    private void normalizeApproved(JsonNode nodes, Map<String, JsonNode> sources, Set<String> seen,
            List<String> parentPath, ArrayNode output, int depth) {
        if (depth > 5 && !nodes.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_DEPTH_EXCEEDED", "메뉴 트리는 최대 5 depth까지 허용됩니다.");
        for (JsonNode proposed : nodes) {
            if (!proposed.path("included").asBoolean(true)) continue;
            String nodeId = proposed.path("nodeId").asText("");
            JsonNode source = sources.get(nodeId);
            String title = proposed.path("title").asText("").trim();
            if (source == null || !seen.add(nodeId) || title.isBlank() || title.length() > 120)
                throw error(HttpStatus.BAD_REQUEST, "MENU_TREE_INVALID", "기능 이름 또는 화면 근거 ID가 올바르지 않습니다.");
            ObjectNode node = mapper.createObjectNode().put("nodeId", nodeId).put("title", title)
                    .put("classification", "UI_MENU").put("menuDepth", depth).put("reviewStatus", "APPROVED");
            node.set("evidence", source.path("evidence").deepCopy());
            ArrayNode featurePath = mapper.createArrayNode();
            parentPath.forEach(featurePath::add); featurePath.add(title);
            node.set("featurePath", featurePath);
            ArrayNode children = mapper.createArrayNode();
            var nextPath = new java.util.ArrayList<>(parentPath); nextPath.add(title);
            normalizeApproved(proposed.path("children"), sources, seen, nextPath, children, depth + 1);
            node.set("children", children); output.add(node);
        }
    }

    public JsonNode approvedTree(UUID id, long memberId, String documentDigest) {
        authorize(id, memberId);
        var rows = jdbc.query("SELECT document_digest, tree_status, tree_json FROM menu_tree_approvals WHERE submission_id=? AND member_id=?",
                (rs, n) -> new StoredTree(memberId, "", rs.getString("document_digest").trim(), parseStoredTree(rs.getString("tree_json"))), id, memberId);
        if (rows.isEmpty() || !"APPROVED".equals(jdbc.queryForObject("SELECT tree_status FROM menu_tree_approvals WHERE submission_id=?", String.class, id)))
            throw error(HttpStatus.CONFLICT, "MENU_TREE_NOT_APPROVED", "TC 생성 전 메뉴 트리를 검토하고 승인해야 합니다.");
        StoredTree tree = rows.get(0);
        if (!tree.digest().equals(documentDigest)) throw error(HttpStatus.CONFLICT, "MENU_TREE_STALE", "문서가 변경되어 메뉴 트리를 다시 승인해야 합니다.");
        if (!EXTERNAL_SCHEMA.equals(tree.tree().path("schemaVersion").asText()))
            throw error(HttpStatus.CONFLICT, "MENU_TREE_EXTERNAL_DRAFT_REQUIRED", "외부 LLM 응답으로 메뉴 트리 초안을 만들어야 TC 프롬프트를 준비할 수 있습니다.");
        return tree.tree();
    }

    private void authorize(UUID id, long memberId) {
        var submission = submissions.findById(SubmissionId.of(id))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "문서 세트를 찾을 수 없습니다."));
        if (!submission.memberId().value().equals(memberId)) throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 문서 세트에 접근할 수 없습니다.");
    }
}
