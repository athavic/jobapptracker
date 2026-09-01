"""The one door between Python and the rest of the system.

Every job goes through this class. It exists so that URL building, error
translation, paging and timeouts are decided once, and so that a job reads as
the business it is doing rather than as HTTP plumbing.
"""

from __future__ import annotations

import logging
from collections.abc import Iterator
from datetime import datetime
from types import TracebackType
from typing import Any, Self

import httpx
from pydantic import ValidationError

from .config import Settings
from .models import (
    Application,
    ApplicationStatus,
    AutomationRun,
    AutomationRunStatus,
    Page,
    ProblemDetail,
    TriggerSource,
)

log = logging.getLogger(__name__)


class ApiError(RuntimeError):
    """A request the API refused.

    Carries the parsed problem detail when there is one, so callers can react to
    *why* they were refused - a 409 on a status change is a race worth skipping,
    a 400 is a bug worth failing on.
    """

    def __init__(self, response: httpx.Response) -> None:
        self.status_code = response.status_code
        self.problem = _parse_problem(response)

        detail = self.problem.detail if self.problem else response.text.strip()
        title = self.problem.title if self.problem else response.reason_phrase
        super().__init__(
            f"{response.request.method} {response.request.url} "
            f"-> {response.status_code} {title}: {detail}"
        )

    @property
    def is_conflict(self) -> bool:
        return self.status_code == 409

    @property
    def is_not_found(self) -> bool:
        return self.status_code == 404


def _parse_problem(response: httpx.Response) -> ProblemDetail | None:
    try:
        return ProblemDetail.model_validate(response.json())
    except (ValueError, ValidationError):
        # A proxy, a 502 from nothing listening, an HTML error page: the error
        # is still real, it just is not a problem detail. Never let failing to
        # parse an error replace the error.
        return None


class JobTrackerClient:
    """A typed, synchronous client for the endpoints workers actually use.

    Synchronous on purpose. These jobs are a bounded sequence of small requests
    against one server; asyncio would add concurrency this workload cannot spend
    and a second set of failure modes to reason about.
    """

    def __init__(
        self,
        settings: Settings,
        client: httpx.Client | None = None,
        *,
        job_name: str | None = None,
    ) -> None:
        self._settings = settings
        self._owns_client = client is None
        self._http = client or httpx.Client(
            base_url=settings.base_url,
            timeout=settings.timeout_seconds,
            headers={"Accept": "application/json"},
            # Retries here cover connection-level failures only - the API was
            # not reached, so nothing can have happened twice. Retrying a 5xx
            # would be a different and more dangerous promise: a POST that
            # timed out may well have been applied, and this API has no
            # idempotency keys to make a repeat safe. So 5xx fails the run and
            # the next scheduled run picks it up.
            transport=httpx.HTTPTransport(retries=2),
        )

        # Every write this client makes is stamped as a bot, and named.
        #
        # Without this the API sees no X-Actor and records the job's writes as
        # HUMAN - and an application this worker ghosted would be indistinguishable
        # from one you ghosted yourself, which is the exact question the events
        # table was added to answer. Set on the session rather than per call so a
        # new endpoint cannot be added without it.
        #
        # Applied to an injected client too: a test that passes its own
        # httpx.Client should exercise the same headers production sends.
        self._http.headers["X-Actor"] = "AUTOMATION"
        if job_name:
            self._http.headers["X-Actor-Detail"] = job_name

    def __enter__(self) -> Self:
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        tb: TracebackType | None,
    ) -> None:
        self.close()

    def close(self) -> None:
        if self._owns_client:
            self._http.close()

    # ---------------------------------------------------------------- requests

    def _request(self, method: str, path: str, **kwargs: Any) -> httpx.Response:
        log.debug("%s %s %s", method, path, kwargs.get("params") or "")
        response = self._http.request(method, path, **kwargs)
        if response.is_error:
            raise ApiError(response)
        return response

    # ----------------------------------------------------------- applications

    def iter_applications(
        self,
        *,
        status: ApplicationStatus | None = None,
        company: str | None = None,
        include_archived: bool = False,
    ) -> Iterator[Application]:
        """Walk every matching application, one page at a time.

        A generator rather than a list so a job can stop early, and so memory
        does not grow with the table. The sort is pinned to ``id,asc``: paging
        over a set ordered by a mutable column (the default is ``createdAt``,
        and any job that writes changes ``updatedAt``) can show the same row
        twice or skip one entirely as rows shift between pages.
        """
        page_number = 0
        while True:
            params: dict[str, Any] = {
                "page": page_number,
                "size": self._settings.page_size,
                "sort": "id,asc",
                "includeArchived": include_archived,
            }
            if status is not None:
                params["status"] = status.value
            if company:
                params["company"] = company

            payload = self._request("GET", "/api/v1/applications", params=params).json()
            page = Page[Application].model_validate(payload)

            yield from page.content

            if page.last or not page.content:
                return
            page_number += 1

    def get_application(self, application_id: int) -> Application:
        response = self._request("GET", f"/api/v1/applications/{application_id}")
        return Application.model_validate(response.json())

    def change_status(
        self,
        application_id: int,
        status: ApplicationStatus,
        note: str | None = None,
    ) -> Application:
        """Move an application through its lifecycle.

        The only way status changes, for Python exactly as for the browser. An
        illegal transition comes back as a 409 from the server's rules, never
        from a check written here.
        """
        body: dict[str, Any] = {"status": status.value}
        if note:
            body["note"] = note

        response = self._request(
            "POST", f"/api/v1/applications/{application_id}/status", json=body
        )
        return Application.model_validate(response.json())

    # -------------------------------------------------------- automation runs

    def start_run(
        self, job_name: str, trigger: TriggerSource, started_at: datetime
    ) -> AutomationRun:
        response = self._request(
            "POST",
            "/api/v1/automation/runs",
            json={
                "jobName": job_name,
                "triggerSource": trigger.value,
                "startedAt": started_at.isoformat(),
            },
        )
        return AutomationRun.model_validate(response.json())

    def complete_run(
        self,
        run_id: int,
        *,
        status: AutomationRunStatus,
        items_scanned: int,
        items_affected: int,
        message: str | None = None,
        details: dict[str, Any] | None = None,
        finished_at: datetime | None = None,
    ) -> AutomationRun:
        body: dict[str, Any] = {
            "status": status.value,
            "itemsScanned": items_scanned,
            "itemsAffected": items_affected,
        }
        if message is not None:
            body["message"] = message
        if details is not None:
            body["details"] = details
        if finished_at is not None:
            body["finishedAt"] = finished_at.isoformat()

        response = self._request(
            "POST", f"/api/v1/automation/runs/{run_id}/complete", json=body
        )
        return AutomationRun.model_validate(response.json())
