"""nudge_stale - find applications that have gone quiet.

The first worker, and deliberately the least clever one: it reads the API, does
arithmetic on dates, and writes a run record. Everything interesting about it is
in what it *refuses* to do.

**It does not decide what "stale" means for the lifecycle.** An application is a
candidate when it has been applied to (``appliedAt`` is set) and is not finished
(the server sent no allowed next statuses). Both facts come from the response.
There is no list of statuses here to fall out of step with the Java enum.

**It does not write by default.** A scan records a run and changes nothing. Only
``--apply`` together with a ``--ghost-after-days`` threshold will move anything,
and even then it asks the server whether GHOSTED is a legal next step rather than
assuming.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import UTC, datetime
from typing import Any

from ..client import ApiError, JobTrackerClient
from ..config import Settings
from ..models import Application, ApplicationStatus
from ..runs import RunOutcome

log = logging.getLogger(__name__)

JOB_NAME = "nudge_stale"


@dataclass(frozen=True)
class StaleApplication:
    application: Application
    days_since_movement: int

    def as_detail(self) -> dict[str, Any]:
        return {
            "id": self.application.id,
            "label": self.application.label(),
            "status": self.application.status.value,
            "daysSinceMovement": self.days_since_movement,
        }


def days_since(moment: datetime, now: datetime) -> int:
    """Whole days between two aware datetimes.

    Both sides are timezone-aware - the API sends UTC instants and ``now`` is
    built with ``datetime.now(UTC)``. Subtracting an aware datetime from a naive
    one raises, which is the good outcome: a silent local-vs-UTC mix-up would
    shift every result by the offset and nobody would notice for months.
    """
    return (now - moment).days


def select_stale(
    applications: list[Application],
    *,
    now: datetime,
    stale_after_days: int,
) -> list[StaleApplication]:
    """The whole decision, as one pure function.

    Kept free of HTTP so it can be tested with a list of models and a fixed
    ``now`` - date logic is exactly the kind of code that is miserable to test
    through a network client and trivial to test directly.
    """
    stale: list[StaleApplication] = []

    for application in applications:
        if application.archived or application.is_terminal:
            continue
        # No appliedAt means nothing has been sent yet. That is a real backlog,
        # but it is a backlog of the user's own inaction rather than of someone
        # else's silence, so it belongs to a different job.
        if application.applied_at is None:
            continue

        quiet_for = days_since(application.last_movement, now)
        if quiet_for >= stale_after_days:
            stale.append(StaleApplication(application, quiet_for))

    # Longest silence first: if a human reads only the top of the list, these
    # are the ones worth reading.
    stale.sort(key=lambda item: item.days_since_movement, reverse=True)
    return stale


def nudge_stale(
    client: JobTrackerClient,
    settings: Settings,
    outcome: RunOutcome,
    *,
    apply: bool = False,
    now: datetime | None = None,
) -> list[StaleApplication]:
    """Scan, report, and - only when asked - ghost the long-dead ones."""
    now = now or datetime.now(UTC)

    applications = list(client.iter_applications())
    outcome.items_scanned = len(applications)

    stale = select_stale(
        applications, now=now, stale_after_days=settings.stale_after_days
    )

    for item in stale:
        log.info(
            "stale %sd  #%d  %s  [%s]",
            item.days_since_movement,
            item.application.id,
            item.application.label(),
            item.application.status.value,
        )

    ghosted: list[int] = []
    skipped: list[dict[str, Any]] = []

    if apply and settings.ghost_after_days is not None:
        ghosted, skipped = _ghost_the_silent(
            client, stale, ghost_after_days=settings.ghost_after_days
        )

    outcome.items_affected = len(ghosted)
    outcome.message = _summarise(
        scanned=outcome.items_scanned,
        stale=len(stale),
        ghosted=len(ghosted),
        apply=apply,
        settings=settings,
    )
    outcome.details = {
        "staleAfterDays": settings.stale_after_days,
        "ghostAfterDays": settings.ghost_after_days,
        "apply": apply,
        "staleCount": len(stale),
        # Capped: details is a dashboard payload, not an archive. The counts
        # above stay exact however long the list gets.
        "stale": [item.as_detail() for item in stale[:50]],
        "ghosted": ghosted,
        "skipped": skipped,
    }

    log.info(outcome.message)
    return stale


def _ghost_the_silent(
    client: JobTrackerClient,
    stale: list[StaleApplication],
    *,
    ghost_after_days: int,
) -> tuple[list[int], list[dict[str, Any]]]:
    """Move applications past the ghost threshold to GHOSTED.

    Idempotent by construction rather than by bookkeeping: GHOSTED is terminal,
    so a second run finds these already finished and ``select_stale`` filters
    them out before this function ever sees them. There is no "already done"
    flag to keep in sync.
    """
    ghosted: list[int] = []
    skipped: list[dict[str, Any]] = []

    for item in stale:
        if item.days_since_movement < ghost_after_days:
            continue

        application = item.application
        if not application.can_move_to(ApplicationStatus.GHOSTED):
            skipped.append(
                {"id": application.id, "reason": "GHOSTED is not a legal next status"}
            )
            continue

        note = (
            f"No movement for {item.days_since_movement} days "
            f"(threshold {ghost_after_days}). Marked by {JOB_NAME}."
        )
        try:
            client.change_status(application.id, ApplicationStatus.GHOSTED, note=note)
            ghosted.append(application.id)
            log.info("ghosted #%d %s", application.id, application.label())
        except ApiError as error:
            # A 409 means a human moved this application between the read and
            # the write. They win: their change is newer and better informed.
            # A conflict is not a job failure, so record it and carry on.
            if not error.is_conflict:
                raise
            skipped.append({"id": application.id, "reason": "changed concurrently"})
            log.warning("skipped #%d: %s", application.id, error)

    return ghosted, skipped


def _summarise(
    *, scanned: int, stale: int, ghosted: int, apply: bool, settings: Settings
) -> str:
    parts = [
        f"{scanned} scanned",
        f"{stale} stale (>= {settings.stale_after_days}d)",
    ]
    if apply and settings.ghost_after_days is not None:
        parts.append(f"{ghosted} ghosted (>= {settings.ghost_after_days}d)")
    elif settings.ghost_after_days is not None:
        parts.append("0 changed (dry run)")
    else:
        parts.append("0 changed (no ghost threshold set)")
    return ", ".join(parts)
