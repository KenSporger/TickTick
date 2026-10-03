from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field, model_validator


class RepeatRule(BaseModel):
    kind: Literal["NONE", "DAILY", "WEEKDAYS", "WEEKLY", "MONTHLY", "YEARLY"] = "NONE"
    weekdays: list[int] = Field(default_factory=list)
    month_day: int | None = Field(default=None, ge=1, le=31)
    total_count: int | None = Field(default=None, ge=1)
    occurrence_index: int = Field(default=1, ge=1)
    interval: int = Field(default=1, ge=1)
    skip_holidays: bool = False
    excluded_dates: list[str] = Field(default_factory=list)
    repeat_until: str | None = None


class TaskPayload(BaseModel):
    id: str
    series_id: str | None = None
    title: str = Field(min_length=1, max_length=500)
    start_date: str | None = None
    end_date: str | None = None
    time: str | None = None
    reminder_at: str | None = None
    repeat_rule: RepeatRule = Field(default_factory=RepeatRule)
    status: Literal["ACTIVE", "COMPLETED", "SKIPPED", "DELETED", "ABANDONED"] = "ACTIVE"
    is_projection: bool = False
    updated_at: str

    @model_validator(mode="after")
    def validate_range(self) -> "TaskPayload":
        if self.start_date and self.end_date and self.end_date < self.start_date:
            raise ValueError("end_date must not precede start_date")
        return self


class SyncRequest(BaseModel):
    tasks: list[TaskPayload]


class SyncResponse(BaseModel):
    tasks: list[TaskPayload]
    server_time: str

