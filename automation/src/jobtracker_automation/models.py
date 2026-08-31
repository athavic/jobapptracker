"""Python mirrors of the API's DTOs.

These are hand-written rather than generated, unlike ``web/src/api/schema.d.ts``.
The tradeoff is deliberate: the front end consumes nearly the whole surface and
benefits from regenerating it wholesale, while a worker touches a handful of
endpoints and gains more from models that carry a little behaviour. What keeps
them honest is ``tests/test_contract.py``, which checks these fields against the
live OpenAPI document.

Two rules hold everywhere in this file:

1. **No lifecycle knowledge.** There is no table of legal transitions here, and
   there must never be one. The server sends ``allowedNextStatuses`` with every
   application, computed from the same enum it enforces, so asking the response
   what is legal cannot drift from what the API will accept.
2. **Tolerant of new fields, strict about known ones.** Unknown JSON keys are
   ignored, because a backend that adds a field should not break a running job.
   An unknown *enum value* is an error, because guessing what a status the
   worker has never heard of means is how automation does damage.
"""

from __future__ import annotations

from datetime import datetime
from decimal import Decimal
from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class ApiModel(BaseModel):
    """Shared config: camelCase on the wire, snake_case in Python."""

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="ignore",
        frozen=True,
    )


class ApplicationStatus(StrEnum):
    DISCOVERED = "DISCOVERED"
    SAVED = "SAVED"
    APPLIED = "APPLIED"
    SCREEN = "SCREEN"
    INTERVIEW = "INTERVIEW"
    OFFER = "OFFER"
    ACCEPTED = "ACCEPTED"
    REJECTED = "REJECTED"
    GHOSTED = "GHOSTED"
    WITHDRAWN = "WITHDRAWN"


class RemoteType(StrEnum):
    ONSITE = "ONSITE"
    HYBRID = "HYBRID"
    REMOTE = "REMOTE"


class SalaryPeriod(StrEnum):
    ANNUAL = "ANNUAL"
    HOURLY = "HOURLY"


class AutomationRunStatus(StrEnum):
    RUNNING = "RUNNING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"


class TriggerSource(StrEnum):
    MANUAL = "MANUAL"
    SCHEDULE = "SCHEDULE"


class CompanySummary(ApiModel):
    id: int
    name: str
    website: str | None = None


class Application(ApiModel):
    id: int
    company: CompanySummary
    role_title: str
    status: ApplicationStatus
    allowed_next_statuses: frozenset[ApplicationStatus]

    source: str | None = None
    job_url: str | None = None
    location: str | None = None
    remote_type: RemoteType | None = None
    salary_min: Decimal | None = None
    salary_max: Decimal | None = None
    currency: str | None = None
    salary_period: SalaryPeriod | None = None
    priority: int
    resume_version: str | None = None
    notes: str | None = None
    applied_at: datetime | None = None
    archived: bool
    created_at: datetime
    updated_at: datetime

    @property
    def is_terminal(self) -> bool:
        """Nothing can follow this status.

        Derived from the server's own answer rather than from a list of terminal
        statuses kept here - see rule 1 at the top of this module.
        """
        return not self.allowed_next_statuses

    def can_move_to(self, status: ApplicationStatus) -> bool:
        return status in self.allowed_next_statuses

    @property
    def last_movement(self) -> datetime:
        """When something last happened to this application.

        ``updated_at`` is the only honest answer the API offers today: it moves
        on a status change and on any edit. Two consequences worth knowing:
        editing a note resets the clock, and *a job that writes to an
        application hides that application's own staleness*. The second is a
        large part of why this worker reads by default and only writes when
        asked to.
        """
        return self.updated_at

    def label(self) -> str:
        return f"{self.company.name} - {self.role_title}"


class Page[T](ApiModel):
    """The API's PageResponse. Generic so one model covers every list endpoint."""

    content: list[T]
    page: int
    size: int
    total_elements: int
    total_pages: int
    first: bool
    last: bool


class AutomationRun(ApiModel):
    id: int
    job_name: str
    status: AutomationRunStatus
    trigger_source: TriggerSource
    started_at: datetime
    finished_at: datetime | None = None
    items_scanned: int
    items_affected: int
    message: str | None = None
    details: dict[str, Any] | None = None


class ProblemDetail(ApiModel):
    """RFC 9457, which is what GlobalExceptionHandler returns for every error.

    Parsing it is what lets the worker log "Cannot move from OFFER to GHOSTED"
    instead of "HTTP 409".
    """

    type: str | None = None
    title: str | None = None
    status: int | None = None
    detail: str | None = None
    instance: str | None = None
    field_errors: dict[str, str] | None = None
