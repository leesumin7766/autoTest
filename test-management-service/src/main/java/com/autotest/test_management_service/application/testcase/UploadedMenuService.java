package com.autotest.test_management_service.application.testcase;

import java.io.ByteArrayInputStream;
import java.util.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.autotest.test_management_service.application.crawler.PlaywrightSetupService;
import com.autotest.test_management_service.application.productdescription.ProductDescriptionException;
import com.autotest.test_management_service.application.service.DocumentDigest;
import com.autotest.test_management_service.application.service.SubmissionPersistenceService;
import com.autotest.test_management_service.domain.submission.SubmissionId;
import com.autotest.test_management_service.domain.submission.SubmissionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** 이미 만들어진 메뉴트리(JSON/XLSX)를 화면 수집 없이 승인된 메뉴트리 작업으로 등록한다. 자료 등록(문서 검증)은 계속 필요하다. */
@Service
public class UploadedMenuService {
    private static final Set<String> TYPES = Set.of("로그인", "주요기능", "부가기능", "CRUD");
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final SubmissionPersistenceService persistence;
    private final SubmissionRepository submissions;
    private final PlaywrightSetupService setups;
    private final FunctionalMenuService menus;

    public UploadedMenuService(JdbcTemplate jdbc, ObjectMapper mapper, SubmissionPersistenceService persistence,
            SubmissionRepository submissions, PlaywrightSetupService setups, FunctionalMenuService menus) {
        this.jdbc = jdbc; this.mapper = mapper; this.persistence = persistence;
        this.submissions = submissions; this.setups = setups; this.menus = menus;
    }

    private ProductDescriptionException error(HttpStatus status, String code, String text) {
        return new ProductDescriptionException(status, code, text);
    }

    public ObjectNode upload(UUID submission, long member, String filename, byte[] bytes, String productName) {
        var found = submissions.findById(SubmissionId.of(submission))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "문서 세트를 찾을 수 없습니다."));
        if (found.memberId().value() != member) throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 문서 세트에 접근할 수 없습니다.");
        setups.get(submission, member); // 자료 등록·검증 상태 확인
        String digest = DocumentDigest.of(persistence.deliveryDocuments(persistence.findDocumentsBySubmissionId(SubmissionId.of(submission))));
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String[] product = {productName == null ? "" : productName.trim()};
        List<JsonNode> parsed;
        try {
            parsed = name.endsWith(".xlsx") ? readExcel(bytes, product) : readJson(bytes, product);
        } catch (ProductDescriptionException known) {
            throw known;
        } catch (Exception bad) {
            throw error(HttpStatus.BAD_REQUEST, "MENU_UPLOAD_INVALID", "메뉴트리 파일을 읽을 수 없습니다. .xlsx(다운로드한 메뉴트리) 또는 .json 파일을 올려주세요.");
        }
        if (parsed.isEmpty()) throw error(HttpStatus.BAD_REQUEST, "MENU_EMPTY", "메뉴트리에서 기능 행을 찾지 못했습니다.");
        if (parsed.size() > 2000) throw error(HttpStatus.BAD_REQUEST, "MENU_ROWS_INVALID", "기능 행은 최대 2,000개까지 허용됩니다.");
        if (product[0].isBlank()) product[0] = "업로드 메뉴트리";
        if (product[0].length() > 120) product[0] = product[0].substring(0, 120);
        ArrayNode rows = mapper.createArrayNode();
        Set<String> seen = new HashSet<>();
        int index = 1;
        for (JsonNode raw : parsed) {
            String fn = raw.path("functionName").asText().trim();
            String description = raw.path("menuDescription").asText().trim();
            if (description.isBlank()) description = fn + " 기능";
            if (fn.isBlank() || fn.length() > 160 || description.length() > 600) throw error(HttpStatus.BAD_REQUEST, "MENU_ROW_INVALID", index + "번째 행의 기능명 또는 설명이 올바르지 않습니다.");
            JsonNode path = raw.path("menuPath");
            if (!path.isArray() || path.size() > 3) throw error(HttpStatus.BAD_REQUEST, "MENU_DEPTH_EXCEEDED", index + "번째 행: 상위 메뉴는 최대 3단계입니다.");
            for (JsonNode part : path) if (!part.isTextual() || part.asText().isBlank() || part.asText().length() > 120) throw error(HttpStatus.BAD_REQUEST, "MENU_PATH_INVALID", index + "번째 행의 메뉴 경로가 올바르지 않습니다.");
            String id = raw.path("rowId").asText("");
            if (id.isBlank() || id.length() > 80 || !seen.add(id)) { id = "UP-" + String.format("%04d", index); seen.add(id); }
            String type = raw.path("featureType").asText("").trim();
            if (!type.isEmpty() && !TYPES.contains(type)) throw error(HttpStatus.BAD_REQUEST, "TC_FEATURE_TYPE_INVALID", index + "번째 행: 기능구분은 로그인·주요기능·부가기능·CRUD 중 하나여야 합니다.");
            ObjectNode row = rows.addObject().put("rowId", id).put("functionName", fn).put("menuDescription", description)
                    .put("location", "UNKNOWN").put("included", raw.path("included").asBoolean(true)).put("reviewStatus", "USER_UPLOADED")
                    .put("rowType", "FUNCTION").put("verificationStatus", "USER_UPLOADED").put("evidenceStatus", "USER_UPLOADED").put("connectionStatus", "NOT_LINKED");
            row.set("menuPath", path.deepCopy());
            if (!type.isEmpty()) row.put("featureType", type);
            row.putArray("evidence"); row.putArray("matchedMenuKeys"); row.putArray("reviewNotes");
            index++;
        }
        if (java.util.stream.StreamSupport.stream(rows.spliterator(), false).noneMatch(r -> r.path("included").asBoolean()))
            throw error(HttpStatus.BAD_REQUEST, "MENU_EMPTY", "한 개 이상의 기능을 포함하세요.");
        ObjectNode snapshot = mapper.createObjectNode().put("productName", product[0]).put("documentDigest", digest)
                .put("rulesVersion", FunctionalMenuService.VERSION).put("targetUrl", "").put("uploaded", true);
        snapshot.putArray("evidence"); snapshot.putArray("warnings").add("사용자가 업로드한 메뉴트리입니다. 화면 수집·문서 근거 검증을 거치지 않았습니다.");
        ObjectNode output = mapper.createObjectNode().put("schemaVersion", FunctionalMenuService.VERSION).put("rulesVersion", FunctionalMenuService.VERSION)
                .put("generationMode", "UPLOADED").put("sourceFile", filename == null ? "" : filename);
        output.set("rows", rows);
        UUID job = UUID.randomUUID();
        jdbc.update("INSERT INTO functional_menu_jobs(job_id,submission_id,member_id,crawl_job_id,document_digest,product_name,rules_version,status,generation_mode,revision,snapshot,output) VALUES(?,?,?,NULL,?,?,?,'APPROVED','UPLOADED',1,?::jsonb,?::jsonb)",
                job, submission, member, digest, product[0], FunctionalMenuService.VERSION, snapshot.toString(), output.toString());
        return menus.status(submission, member, job);
    }

    private List<JsonNode> readJson(byte[] bytes, String[] product) throws Exception {
        JsonNode root = mapper.readTree(bytes);
        JsonNode rows = root;
        if (root.isObject()) {
            if (product[0].isBlank()) product[0] = root.path("productName").asText("").trim();
            rows = root.has("rows") ? root.path("rows") : root.path("output").path("rows");
        }
        if (!rows.isArray()) throw error(HttpStatus.BAD_REQUEST, "MENU_UPLOAD_INVALID", "JSON에는 rows 배열이 필요합니다.");
        List<JsonNode> list = new ArrayList<>();
        rows.forEach(list::add);
        return list;
    }

    // 다운로드한 메뉴트리 형식: 1depth(제품명) ~ 5depth(기능명) + 메뉴 설명. 병합 셀은 위쪽 값을 이어받는다.
    private List<JsonNode> readExcel(byte[] bytes, String[] product) throws Exception {
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = book.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) return List.of();
            DataFormatter fmt = new DataFormatter();
            List<Integer> depth = new ArrayList<>();
            int description = -1;
            for (Cell cell : header) {
                String text = fmt.formatCellValue(cell).trim().toLowerCase(Locale.ROOT).replace(" ", "");
                if (text.matches("\\d+depth")) depth.add(cell.getColumnIndex());
                else if (text.equals("메뉴설명") || text.equals("설명")) description = cell.getColumnIndex();
            }
            if (depth.size() < 2) throw error(HttpStatus.BAD_REQUEST, "MENU_UPLOAD_INVALID", "1depth~5depth 열이 있는 메뉴트리 파일이 필요합니다.");
            List<JsonNode> list = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                List<String> cells = new ArrayList<>();
                for (int c : depth) cells.add(value(sheet, r, c, fmt));
                String desc = description < 0 ? "" : value(sheet, r, description, fmt);
                if (cells.stream().allMatch(String::isBlank)) continue;
                if (product[0].isBlank() && !cells.get(0).isBlank() && !"-".equals(cells.get(0))) product[0] = cells.get(0);
                List<String> names = new ArrayList<>();
                for (int i = 1; i < cells.size(); i++) if (!cells.get(i).isBlank() && !"-".equals(cells.get(i))) names.add(cells.get(i));
                // 기능명 열이 '-'이면 메뉴 구조 행(기능 아님)
                if ("-".equals(cells.get(cells.size() - 1)) && cells.size() >= 5) continue;
                if (names.isEmpty()) continue;
                ObjectNode row = mapper.createObjectNode().put("functionName", names.get(names.size() - 1)).put("menuDescription", desc);
                var path = row.putArray("menuPath");
                for (int i = 0; i < names.size() - 1; i++) path.add(names.get(i));
                list.add(row);
            }
            return list;
        }
    }

    private String value(Sheet sheet, int rowIndex, int col, DataFormatter fmt) {
        for (var region : sheet.getMergedRegions()) {
            if (region.isInRange(rowIndex, col)) {
                Row first = sheet.getRow(region.getFirstRow());
                return first == null || first.getCell(col) == null ? "" : fmt.formatCellValue(first.getCell(col)).trim();
            }
        }
        Row row = sheet.getRow(rowIndex);
        return row == null || row.getCell(col) == null ? "" : fmt.formatCellValue(row.getCell(col)).trim();
    }
}
