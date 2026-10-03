"""PDF output strategy. Every visual setting comes from the presentation part of the template snapshot."""
import io
from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4, LETTER
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas as pdf_canvas
from reportlab.platypus import KeepTogether, ListFlowable, ListItem, PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

from .document_model import GeneratedDocument
from .rendering import RenderError

PAGE_SIZES = {"A4": A4, "LETTER": LETTER}


class PdfRenderer:
    format = "PDF"
    media_type = "application/pdf"

    def render(self, document: dict, presentation: dict) -> bytes:
        try:
            model = GeneratedDocument.model_validate(document)
        except Exception as error:
            raise RenderError("저장된 문서 데이터가 올바르지 않습니다.") from error
        regular, bold = _register_fonts(presentation["fonts"])
        styles = _styles(presentation, regular, bold)
        margins = presentation["page"]["marginsMm"]
        buffer = io.BytesIO()
        banner = model.generation.label if presentation["header"].get("showBanner", True) else None
        header_title = model.title if presentation["header"].get("showTitle", True) else None
        footer_format = presentation["footer"].get("pageNumberFormat", "{page} / {pages}")

        def make_canvas(*args, **kwargs):
            return _NumberedCanvas(*args, regular=regular, header_title=header_title, banner=banner,
                                   footer_format=footer_format, margins=margins, bold=bold, **kwargs)

        doc = SimpleDocTemplate(
            buffer, pagesize=PAGE_SIZES.get(presentation["page"].get("size", "A4"), A4),
            leftMargin=margins["left"] * mm, rightMargin=margins["right"] * mm,
            topMargin=margins["top"] * mm, bottomMargin=margins["bottom"] * mm,
            title=model.title, author="autotest")
        flow = self._story(model, presentation, styles, doc.width, regular, bold)
        try:
            doc.build(flow, canvasmaker=make_canvas)
        except Exception as error:
            raise RenderError("PDF 출력에 실패했습니다.") from error
        return buffer.getvalue()

    def _story(self, model, presentation, styles, width, regular, bold) -> list:
        flow = [Paragraph(escape(model.title), styles["title"])]
        if model.generation.label:
            flow.append(Paragraph(escape(model.generation.label), styles["banner"]))
        numbering = presentation.get("headingNumbering", False)
        page_breaks = set(presentation.get("pageBreakAfterSections", []))
        for index, section in enumerate(model.sections, start=1):
            heading = f"{index}. {section.title}" if numbering else section.title
            first = True
            for block in section.blocks:
                items = self._block(block, styles, width, presentation, regular, bold)
                if first:
                    # Keep the heading with the start of its content.
                    flow.append(KeepTogether([Paragraph(escape(heading), styles["heading"]), *items[:1]]))
                    flow.extend(items[1:])
                    first = False
                else:
                    flow.extend(items)
            if presentation.get("showSources", True) and section.sources:
                labels = ", ".join(f"{s.role or ''} {s.originalFilename or s.fileId}".strip() for s in section.sources)
                flow.append(Paragraph(escape(f"출처: {labels}"), styles["sources"]))
            if section.id in page_breaks:
                flow.append(PageBreak())
        return flow

    def _block(self, block, styles, width, presentation, regular, bold) -> list:
        if block.type == "paragraph":
            return [Paragraph(escape(block.text).replace("\n", "<br/>"), styles["body"])]
        if block.type == "bullets":
            items = [ListItem(Paragraph(escape(item), styles["bullet"]), leftIndent=styles["bullet"].leftIndent)
                     for item in block.items]
            return [ListFlowable(items, bulletType="bullet", start="•", leftIndent=14, bulletFontName=regular),
                    Spacer(1, 4)]
        return [self._table(block, styles, width, presentation["table"]), Spacer(1, 8)]

    def _table(self, block, styles, width, table_style) -> Table:
        header = [Paragraph(f"<b>{escape(c)}</b>", styles["tableHeader"]) for c in block.columns]
        rows = [[Paragraph(escape(cell).replace("\n", "<br/>"), styles["tableCell"]) for cell in row]
                for row in block.rows]
        widths = block.widths or [1] * len(block.columns)
        total = float(sum(widths))
        table = Table([header, *rows], colWidths=[width * w / total for w in widths], repeatRows=1, splitByRow=1)
        pad = table_style.get("padding", 5)
        commands = [
            ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor(table_style["gridColor"])),
            ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor(table_style["headerBackground"])),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("LEFTPADDING", (0, 0), (-1, -1), pad), ("RIGHTPADDING", (0, 0), (-1, -1), pad),
            ("TOPPADDING", (0, 0), (-1, -1), pad), ("BOTTOMPADDING", (0, 0), (-1, -1), pad),
        ]
        zebra = table_style.get("zebraBackground")
        if zebra:
            for row_index in range(2, len(rows) + 1, 2):
                commands.append(("BACKGROUND", (0, row_index), (-1, row_index), colors.HexColor(zebra)))
        table.setStyle(TableStyle(commands))
        return table


def _register_fonts(fonts: dict) -> tuple[str, str]:
    names = []
    for key in ("regular", "bold"):
        spec = fonts[key]
        path = next((p for p in spec["candidates"] if Path(p).is_file()), None)
        if path is None:
            raise RenderError("템플릿에 지정된 한글 글꼴을 찾을 수 없습니다.")
        if spec["name"] not in pdfmetrics.getRegisteredFontNames():
            pdfmetrics.registerFont(TTFont(spec["name"], path))
        names.append(spec["name"])
    pdfmetrics.registerFontFamily(names[0], normal=names[0], bold=names[1], italic=names[0], boldItalic=names[1])
    return names[0], names[1]


def _styles(presentation: dict, regular: str, bold: str) -> dict:
    styles = {}
    wrap = presentation.get("wordWrap")
    for name, spec in presentation["styles"].items():
        styles[name] = ParagraphStyle(
            name, fontName=bold if name in ("title", "heading") else regular, fontSize=spec["size"],
            leading=spec["leading"], textColor=colors.HexColor(spec["color"]), alignment=TA_LEFT,
            spaceBefore=spec.get("spaceBefore", 0), spaceAfter=spec.get("spaceAfter", 0),
            leftIndent=spec.get("indent", 0), wordWrap=wrap, splitLongWords=1)
    return styles


class _NumberedCanvas(pdf_canvas.Canvas):
    def __init__(self, *args, regular, bold, header_title, banner, footer_format, margins, **kwargs):
        super().__init__(*args, **kwargs)
        self._saved_pages = []
        self._decor = (regular, bold, header_title, banner, footer_format, margins)

    def showPage(self):
        self._saved_pages.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        total = len(self._saved_pages)
        for state in self._saved_pages:
            self.__dict__.update(state)
            self._draw_decor(total)
            super().showPage()
        super().save()

    def _draw_decor(self, total: int) -> None:
        regular, bold, header_title, banner, footer_format, margins = self._decor
        width, height = self._pagesize
        left, right = margins["left"] * mm, width - margins["right"] * mm
        self.setFont(regular, 8)
        self.setFillColor(colors.HexColor("#6B7280"))
        if header_title:
            self.drawString(left, height - 12 * mm, header_title)
        if banner:
            self.setFillColor(colors.HexColor("#B91C1C"))
            self.drawRightString(right, height - 12 * mm, banner)
        self.setFillColor(colors.HexColor("#6B7280"))
        self.drawCentredString(width / 2, 10 * mm, footer_format.format(page=self._pageNumber, pages=total))
