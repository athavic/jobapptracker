"""Shared fixtures.

The API responses here are hand-written dictionaries in the *wire* shape -
camelCase, string enums, ISO instants - rather than models built directly. That
is the point: if the real API's JSON stops matching this, the models stop
parsing it, and the test fails for the same reason production would.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from typing import Any

import pytest

from jobtracker_automation.config import Settings

NOW = datetime(2026, 8, 30, 12, 0, tzinfo=UTC)

#: What the server sends for a status that can still move. Copied from a real
#: response rather than derived, so a change to the Java lifecycle shows up here
#: as a failing contract test instead of being silently mirrored.
ACTIVE_NEXT = ["SCREEN", "INTERVIEW", "OFFER", "REJECTED", "GHOSTED", "WITHDRAWN"]


@pytest.fixture
def settings() -> Settings:
    return Settings(
        api_base_url="http://api.test",
        stale_after_days=14,
        ghost_after_days=45,
        page_size=2,
    )


def application_json(
    application_id: int,
    *,
    status: str = "APPLIED",
    allowed_next: list[str] | None = None,
    days_quiet: int = 0,
    applied: bool = True,
    archived: bool = False,
    company: str = "Acme",
) -> dict[str, Any]:
    updated_at = NOW - timedelta(days=days_quiet)
    return {
        "id": application_id,
        "company": {"id": 1, "name": company, "website": None},
        "roleTitle": "Backend Engineer",
        "status": status,
        "allowedNextStatuses": ACTIVE_NEXT if allowed_next is None else allowed_next,
        "source": None,
        "jobUrl": None,
        "location": None,
        "remoteType": None,
        "salaryMin": None,
        "salaryMax": None,
        "currency": None,
        "salaryPeriod": None,
        "priority": 3,
        "resumeVersion": None,
        "notes": None,
        "appliedAt": (NOW - timedelta(days=days_quiet)).isoformat() if applied else None,
        "archived": archived,
        "createdAt": (NOW - timedelta(days=days_quiet + 1)).isoformat(),
        "updatedAt": updated_at.isoformat(),
    }


def page_json(items: list[dict[str, Any]], *, page: int, size: int, total: int) -> dict[str, Any]:
    total_pages = max(1, -(-total // size))
    return {
        "content": items,
        "page": page,
        "size": size,
        "totalElements": total,
        "totalPages": total_pages,
        "first": page == 0,
        "last": page >= total_pages - 1,
    }


def run_json(run_id: int = 1, status: str = "RUNNING") -> dict[str, Any]:
    return {
        "id": run_id,
        "jobName": "nudge_stale",
        "status": status,
        "triggerSource": "MANUAL",
        "startedAt": NOW.isoformat(),
        "finishedAt": None if status == "RUNNING" else NOW.isoformat(),
        "itemsScanned": 0,
        "itemsAffected": 0,
        "message": None,
        "details": None,
    }
