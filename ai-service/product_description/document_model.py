"""Generic structured document model shared by LLM output validation, storage, and output strategies."""
from typing import Annotated, Literal, Union

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

Text = Annotated[str, Field(min_length=1, max_length=4000)]
Cell = Annotated[str, Field(max_length=1000)]


class Strict(BaseModel):
    model_config = ConfigDict(extra="forbid")


class ParagraphBlock(Strict):
    type: Literal["paragraph"]
    text: Text


class BulletsBlock(Strict):
    type: Literal["bullets"]
    items: Annotated[list[Annotated[str, Field(min_length=1, max_length=1000)]], Field(min_length=1, max_length=50)]


class TableBlock(Strict):
    type: Literal["table"]
    columns: Annotated[list[Annotated[str, Field(min_length=1, max_length=100)]], Field(min_length=1, max_length=8)]
    rows: Annotated[list[list[Cell]], Field(max_length=200)]
    widths: list[float] | None = None

    @model_validator(mode="after")
    def rows_match_columns(self):
        if any(len(row) != len(self.columns) for row in self.rows):
            raise ValueError("Every table row must have one cell per column")
        if self.widths is not None and (len(self.widths) != len(self.columns) or any(w <= 0 for w in self.widths)):
            raise ValueError("Table widths must match the columns")
        return self


Block = Annotated[Union[ParagraphBlock, BulletsBlock, TableBlock], Field(discriminator="type")]


class SourceRef(Strict):
    fileId: str
    role: str | None = None
    originalFilename: str | None = None


class Section(Strict):
    id: str
    title: str
    blocks: list[Block]
    sources: list[SourceRef] = []


class TemplateRef(Strict):
    id: str
    version: str
    digest: str


class GenerationInfo(Strict):
    mode: Literal["MOCK", "REAL"]
    label: str | None = None
    provider: str
    model: str
    generatedAt: str


class GeneratedDocument(Strict):
    schemaVersion: Literal[1] = 1
    template: TemplateRef
    title: str
    generation: GenerationInfo
    sections: list[Section]

    @field_validator("sections")
    @classmethod
    def unique_section_ids(cls, sections):
        ids = [section.id for section in sections]
        if len(ids) != len(set(ids)):
            raise ValueError("Section ids must be unique")
        return sections


class LlmSection(Strict):
    id: str
    blocks: list[Block]
    sources: list[SourceRef] = []


class LlmOutput(Strict):
    sections: list[LlmSection]
