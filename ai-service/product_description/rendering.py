"""Output strategy boundary: the same stored document data can be rendered by any registered format."""
from typing import Protocol


class RenderError(Exception):
    code = "RENDER_FAILED"


class DocumentRenderer(Protocol):
    format: str
    media_type: str

    def render(self, document: dict, presentation: dict) -> bytes:
        ...


def get_renderer(output_format: str) -> DocumentRenderer:
    # Register future strategies (for example DOCX) here.
    from .pdf_renderer import PdfRenderer
    renderers = {"PDF": PdfRenderer()}
    try:
        return renderers[output_format.upper()]
    except KeyError as error:
        raise RenderError(f"Unsupported output format: {output_format}") from error
