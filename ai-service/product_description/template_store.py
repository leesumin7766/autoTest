"""Versioned template package loading. Content structure and PDF presentation live in doc_templates/."""
import hashlib
import json
import os
import re
from pathlib import Path

DEFAULT_TEMPLATE_ID = "product-description"
TEMPLATE_ROOT = Path(os.environ.get("DOC_TEMPLATE_ROOT", Path(__file__).resolve().parent.parent / "doc_templates"))
SYSTEM_SECTIONS = {"document_info", "reference_documents"}
BLOCK_TYPES = {"paragraph", "bullets", "table"}
SECTION_ID = re.compile(r"^[a-z][a-z0-9_]{0,39}$")
PRESENTATION_KEYS = {"page", "fonts", "styles", "table", "header", "footer"}
STYLE_NAMES = {"title", "banner", "heading", "body", "bullet", "sources", "tableHeader", "tableCell"}


class TemplateError(Exception):
    pass


def load_template(template_id: str = DEFAULT_TEMPLATE_ID, root: Path | None = None) -> dict:
    """Read the package from disk on every call so edits apply to the next generation."""
    base = (root or TEMPLATE_ROOT) / template_id
    if not re.fullmatch(r"[a-z0-9-]+", template_id) or not base.is_dir():
        raise TemplateError(f"Unknown template: {template_id}")
    try:
        manifest = _read_json(base / "manifest.json")
        content = _read_json(base / "content.json")
        presentation = _read_json(base / "presentation.json")
    except (OSError, ValueError) as error:
        raise TemplateError(f"Template package could not be read: {template_id}") from error
    snapshot = {"manifest": manifest, "content": content, "presentation": presentation}
    _validate(snapshot, template_id)
    canonical = json.dumps(snapshot, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    snapshot["digest"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    return snapshot


def _read_json(path: Path) -> dict:
    with path.open(encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError("Template file must contain a JSON object")
    return value


def _validate(snapshot: dict, template_id: str) -> None:
    manifest, content, presentation = snapshot["manifest"], snapshot["content"], snapshot["presentation"]
    if manifest.get("templateId") != template_id or not manifest.get("version"):
        raise TemplateError("Template manifest must declare matching templateId and a version")
    if "PDF" not in manifest.get("outputFormats", []):
        raise TemplateError("Template manifest must list the PDF output format")
    for key in ("documentTitle", "unknownText", "mockLabel"):
        if not isinstance(content.get(key), str) or not content[key].strip():
            raise TemplateError(f"content.{key} is required")
    if not isinstance(content.get("llmInstructions"), list) or not content["llmInstructions"]:
        raise TemplateError("content.llmInstructions is required")
    sections = content.get("sections")
    if not isinstance(sections, list) or not sections:
        raise TemplateError("content.sections must be a non-empty list")
    seen = set()
    for section in sections:
        section_id = section.get("id", "")
        if not SECTION_ID.match(section_id) or section_id in seen:
            raise TemplateError(f"Invalid or duplicate section id: {section_id!r}")
        seen.add(section_id)
        if not section.get("title"):
            raise TemplateError(f"Section {section_id} needs a title")
        generator = section.get("generator")
        if generator == "system":
            if section.get("system") not in SYSTEM_SECTIONS:
                raise TemplateError(f"Section {section_id} uses an unknown system generator")
        elif generator == "llm":
            _validate_blocks(section_id, section.get("blocks"))
            if not section.get("guideline"):
                raise TemplateError(f"Section {section_id} needs a writing guideline")
        else:
            raise TemplateError(f"Section {section_id} has an unknown generator")
    missing = PRESENTATION_KEYS - presentation.keys()
    if missing:
        raise TemplateError(f"presentation is missing: {sorted(missing)}")
    if STYLE_NAMES - presentation["styles"].keys():
        raise TemplateError("presentation.styles is missing a required style")
    for page_break in presentation.get("pageBreakAfterSections", []):
        if page_break not in seen:
            raise TemplateError(f"pageBreakAfterSections references unknown section {page_break}")


def _validate_blocks(section_id: str, blocks) -> None:
    if not isinstance(blocks, list) or not blocks:
        raise TemplateError(f"Section {section_id} needs at least one block")
    for block in blocks:
        if block.get("type") not in BLOCK_TYPES:
            raise TemplateError(f"Section {section_id} has an unknown block type")
        if block["type"] == "table":
            columns = block.get("columns")
            if not isinstance(columns, list) or not columns or not all(isinstance(c, str) and c for c in columns):
                raise TemplateError(f"Table in section {section_id} needs column names")
            widths = block.get("widths")
            if widths is not None and (len(widths) != len(columns) or not all(isinstance(w, (int, float)) and w > 0 for w in widths)):
                raise TemplateError(f"Table widths in section {section_id} must match the columns")
