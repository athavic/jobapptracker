"""Recording a job execution, so that "did it run?" is an answerable question."""

from __future__ import annotations

import logging
from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any

import httpx

from .client import ApiError, JobTrackerClient
from .models import AutomationRun, AutomationRunStatus, TriggerSource

log = logging.getLogger(__name__)


@dataclass
class RunOutcome:
    """The mutable scratchpad a job fills in while it works.

    The job body writes to this as it goes rather than returning a value at the
    end, so that a run which fails halfway still reports the counts it reached.
    """

    items_scanned: int = 0
    #: How many records the job *changed*. Not how many it found worth
    #: reporting - a dry run finds plenty and changes nothing, and conflating
    #: the two would make the dashboard's numbers a lie.
    items_affected: int = 0
    message: str | None = None
    details: dict[str, Any] = field(default_factory=dict)


@contextmanager
def record_run(
    client: JobTrackerClient,
    job_name: str,
    *,
    trigger: TriggerSource = TriggerSource.MANUAL,
    enabled: bool = True,
) -> Iterator[RunOutcome]:
    """Bracket a job with a start call and a matching complete call.

    Written as a context manager because the guarantee wanted here is exactly
    the one ``try/finally`` gives: whatever happens inside, the run gets an
    outcome. An exception is recorded as FAILED and then re-raised - swallowing
    it would leave the process exiting 0 while the dashboard said FAILED.

    ``enabled=False`` skips the bookkeeping entirely, for experimenting locally
    without leaving rows behind.
    """
    started_at = datetime.now(UTC)
    outcome = RunOutcome()

    run: AutomationRun | None = None
    if enabled:
        run = client.start_run(job_name, trigger, started_at)
        log.info("run %s started (%s, %s)", run.id, job_name, trigger.value)

    try:
        yield outcome
    except BaseException as exc:
        # BaseException, not Exception: a Ctrl-C or a SystemExit is still a run
        # that did not finish its work, and leaving the row RUNNING forever
        # would be a worse record than FAILED.
        _finish(client, run, outcome, AutomationRunStatus.FAILED, error=exc)
        raise
    else:
        _finish(client, run, outcome, AutomationRunStatus.SUCCEEDED)


def _finish(
    client: JobTrackerClient,
    run: AutomationRun | None,
    outcome: RunOutcome,
    status: AutomationRunStatus,
    error: BaseException | None = None,
) -> None:
    if run is None:
        return

    message = outcome.message
    if error is not None:
        message = f"{type(error).__name__}: {error}"

    try:
        client.complete_run(
            run.id,
            status=status,
            items_scanned=outcome.items_scanned,
            items_affected=outcome.items_affected,
            message=message,
            details=outcome.details or None,
            finished_at=datetime.now(UTC),
        )
        log.info(
            "run %s %s - %d scanned, %d changed",
            run.id,
            status.value.lower(),
            outcome.items_scanned,
            outcome.items_affected,
        )
    except (ApiError, httpx.HTTPError) as report_failure:
        # If the API is what broke, this call fails too. Log and move on: the
        # original failure is the one that matters, and it must not be replaced
        # by an error raised while trying to report it. The row stays RUNNING,
        # which is itself the correct signal - the job did not report back.
        log.error("could not record the outcome of run %s: %s", run.id, report_failure)
