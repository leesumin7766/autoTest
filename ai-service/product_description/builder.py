"""Builds the LLM request from a template snapshot and assembles the validated document from LLM output."""
from datetime import datetime, timezone

from pydantic import ValidationError

from .document_model import GeneratedDocument, LlmOutput
from .llm import LlmProvider, LlmRequest


class InvalidLlmOutput(Exception):
    code = "LLM_RESPONSE_INVALID"


def build_llm_request(snapshot: dict, documents: list[dict], warnings: list[dict]) -> LlmRequest:
    content = snapshot["content"]
    unknown = content["unknownText"]
    specs = [
        {"id": section["id"], "title": section["title"],
         "guideline": section["guideline"].replace("{unknownText}", unknown), "blocks": section["blocks"]}
        for section in content["sections"] if section["generator"] == "llm"
    ]
    instructions = "\n".join(line.replace("{unknownText}", unknown) for line in content["llmInstructions"])
    instructions += (
        "\n각 section은 요청한 blocks와 같은 순서·같은 type으로 작성한다. "
        "paragraph는 {type,text}, bullets는 {type,items}, table은 {type,columns,rows}이며 columns는 요청과 동일해야 한다. "
        "반환 형식: {\"sections\":[{\"id\":..., \"blocks\":[...], \"sources\":[{\"fileId\":...}]}]}")
    return LlmRequest(
        instructions=instructions,
        sections=specs,
        documents=[{"fileId": d["fileId"], "role": d["role"], "originalFilename": d["originalFilename"],
                    "text": d["extractedText"]} for d in documents],
        warnings=warnings,
        unknown_text=unknown,
        pipeline=content.get("pipeline"),
    )


def assemble_document(snapshot: dict, provider: LlmProvider, llm_raw: dict, context: dict) -> dict:
    content, manifest = snapshot["content"], snapshot["manifest"]
    documents = {d["fileId"]: d for d in context["documents"]}
    try:
        llm_output = LlmOutput.model_validate(llm_raw)
    except ValidationError as error:
        raise InvalidLlmOutput("LLM 응답이 문서 구조 형식과 맞지 않습니다.") from error
    by_id = {section.id: section for section in llm_output.sections}
    expected_ids = [s["id"] for s in content["sections"] if s["generator"] == "llm"]
    if len(by_id) != len(llm_output.sections) or set(by_id) != set(expected_ids):
        raise InvalidLlmOutput("LLM 응답의 섹션이 템플릿과 일치하지 않습니다.")

    sections = []
    for spec in content["sections"]:
        if spec["generator"] == "system":
            sections.append(_system_section(spec, snapshot, provider, context, documents))
        else:
            sections.append(_llm_section(spec, by_id[spec["id"]].model_dump(), content, documents, context))

    generation = {
        "mode": provider.mode,
        "label": content["mockLabel"] if provider.mode == "MOCK" else None,
        "provider": provider.name,
        "model": provider.model,
        "generatedAt": context["generatedAt"],
    }
    document = GeneratedDocument.model_validate({
        "template": {"id": manifest["templateId"], "version": manifest["version"], "digest": snapshot["digest"]},
        "title": content["documentTitle"],
        "generation": generation,
        "sections": sections,
    })
    return document.model_dump(mode="json")


def _llm_section(spec: dict, output: dict, content: dict, documents: dict, context: dict) -> dict:
    spec_blocks = spec["blocks"]
    if len(output["blocks"]) != len(spec_blocks):
        raise InvalidLlmOutput(f"섹션 {spec['id']}의 블록 구성이 템플릿과 다릅니다.")
    blocks = []
    for spec_block, block in zip(spec_blocks, output["blocks"]):
        if block["type"] != spec_block["type"]:
            raise InvalidLlmOutput(f"섹션 {spec['id']}의 블록 유형이 템플릿과 다릅니다.")
        if block["type"] == "table":
            if block["columns"] != spec_block["columns"]:
                raise InvalidLlmOutput(f"섹션 {spec['id']}의 표 열이 템플릿과 다릅니다.")
            block = {**block, "widths": spec_block.get("widths")}
        blocks.append(block)
    sources = _resolve_sources(output["sources"], documents, content)
    if spec.get("appendPreflightWarnings") and context["warnings"]:
        text = content["systemText"]
        rows = [[w.get("code", ""), _role_label(w.get("role"), content, text["warningTarget"]), w.get("message", "")]
                for w in context["warnings"]]
        blocks.append({"type": "table", "columns": text["warningColumns"], "rows": rows,
                       "widths": text["warningWidths"]})
    return {"id": spec["id"], "title": spec["title"], "blocks": blocks, "sources": sources}


def _resolve_sources(refs: list[dict], documents: dict, content: dict) -> list[dict]:
    resolved, seen = [], set()
    for ref in refs:
        document = documents.get(ref["fileId"])
        if document is None:
            raise InvalidLlmOutput("LLM 응답이 제공되지 않은 문서를 출처로 지정했습니다.")
        if ref["fileId"] not in seen:
            seen.add(ref["fileId"])
            resolved.append({"fileId": document["fileId"], "role": document["role"],
                             "originalFilename": document["originalFilename"]})
    return resolved


def _role_label(role, content: dict, fallback: str) -> str:
    return content["roleLabels"].get(role, role) if role else fallback


def _system_section(spec: dict, snapshot: dict, provider: LlmProvider, context: dict, documents: dict) -> dict:
    content, manifest = snapshot["content"], snapshot["manifest"]
    text = content["systemText"]
    all_sources = [{"fileId": d["fileId"], "role": d["role"], "originalFilename": d["originalFilename"]}
                   for d in documents.values()]
    if spec["system"] == "document_info":
        mode = content["mockLabel"] if provider.mode == "MOCK" else f"{provider.name} / {provider.model}"
        decision = text["preflightDecisions"].get(context["decision"], context["decision"])
        rows = [
            [text["productId"], str(context["productId"])],
            [text["submissionId"], context["submissionId"]],
            [text["generatedAt"], context["generatedAt"]],
            [text["generationMode"], mode],
            [text["template"], f"{manifest['templateId']} v{manifest['version']}"],
            [text["preflight"], decision],
        ]
        block = {"type": "table", "columns": text["infoColumns"], "rows": rows, "widths": text["infoWidths"]}
    else:
        rows = [[_role_label(d["role"], content, d["role"]), d["originalFilename"], d["format"], d["fileId"]]
                for d in documents.values()]
        block = {"type": "table", "columns": text["referenceColumns"], "rows": rows, "widths": text["referenceWidths"]}
    return {"id": spec["id"], "title": spec["title"], "blocks": [block], "sources": all_sources}


def now_utc() -> str:
    return datetime.now(timezone.utc).replace(microsecond=0).isoformat()
