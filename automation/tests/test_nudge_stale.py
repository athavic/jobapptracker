"""The job end to end, against a mocked API.

These tests are about the seam rather than the arithmetic - what the worker
sends, and how it behaves when the server says no.
"""

from __future__ import annotations

import json

import httpx
import pytest
import respx
from conftest import NOW, application_json, page_json, run_json

from jobtracker_automation.client import ApiError, JobTrackerClient
from jobtracker_automation.jobs.nudge_stale import JOB_NAME, nudge_stale
from jobtracker_automation.models import TriggerSource
from jobtracker_automation.runs import RunOutcome, record_run

BASE = "http://api.test"


def mock_applications(*applications):
    return respx.get(f"{BASE}/api/v1/applications").mock(
        return_value=httpx.Response(
            200,
            json=page_json(
                list(applications), page=0, size=100, total=len(applications)
            ),
        )
    )


@respx.mock
def test_a_scan_reports_without_changing_anything(settings):
    mock_applications(
        application_json(1, days_quiet=60),
        application_json(2, days_quiet=3),
    )
    status_route = respx.post(f"{BASE}/api/v1/applications/1/status")

    outcome = RunOutcome()
    with JobTrackerClient(settings) as client:
        nudge_stale(client, settings, outcome, apply=False, now=NOW)

    assert outcome.items_scanned == 2
    # Found one, changed none. items_affected counts changes, never findings.
    assert outcome.items_affected == 0
    assert outcome.details["staleCount"] == 1
    assert not status_route.called


@respx.mock
def test_apply_ghosts_only_past_the_ghost_threshold(settings):
    mock_applications(
        application_json(1, days_quiet=60),  # past ghost_after_days=45
        application_json(2, days_quiet=20),  # stale, but not ghosted yet
    )
    ghosting = respx.post(f"{BASE}/api/v1/applications/1/status").mock(
        return_value=httpx.Response(
            200, json=application_json(1, status="GHOSTED", allowed_next=[])
        )
    )

    outcome = RunOutcome()
    with JobTrackerClient(settings) as client:
        nudge_stale(client, settings, outcome, apply=True, now=NOW)

    assert outcome.items_affected == 1
    assert outcome.details["ghosted"] == [1]
    assert ghosting.call_count == 1

    body = json.loads(ghosting.calls[0].request.content)
    assert body["status"] == "GHOSTED"
    assert JOB_NAME in body["note"]


@respx.mock
def test_a_conflict_is_skipped_rather_than_fatal(settings):
    """A human moved it between our read and our write. They win; the run survives."""
    mock_applications(application_json(1, days_quiet=60))
    respx.post(f"{BASE}/api/v1/applications/1/status").mock(
        return_value=httpx.Response(
            409, json={"title": "Illegal status transition", "detail": "already OFFER"}
        )
    )

    outcome = RunOutcome()
    with JobTrackerClient(settings) as client:
        nudge_stale(client, settings, outcome, apply=True, now=NOW)

    assert outcome.items_affected == 0
    assert outcome.details["skipped"] == [{"id": 1, "reason": "changed concurrently"}]


@respx.mock
def test_a_non_conflict_error_fails_the_job(settings):
    """A 400 means the worker is wrong. Do not paper over it."""
    mock_applications(application_json(1, days_quiet=60))
    respx.post(f"{BASE}/api/v1/applications/1/status").mock(
        return_value=httpx.Response(400, json={"title": "Invalid request"})
    )

    with JobTrackerClient(settings) as client:
        with pytest.raises(ApiError):
            nudge_stale(client, settings, RunOutcome(), apply=True, now=NOW)


@respx.mock
def test_the_run_is_started_before_the_work_and_completed_after(settings):
    start = respx.post(f"{BASE}/api/v1/automation/runs").mock(
        return_value=httpx.Response(201, json=run_json(9))
    )
    complete = respx.post(f"{BASE}/api/v1/automation/runs/9/complete").mock(
        return_value=httpx.Response(200, json=run_json(9, status="SUCCEEDED"))
    )
    mock_applications(application_json(1, days_quiet=60))

    with JobTrackerClient(settings) as client:
        with record_run(client, JOB_NAME, trigger=TriggerSource.SCHEDULE) as outcome:
            nudge_stale(client, settings, outcome, apply=False, now=NOW)

    assert json.loads(start.calls[0].request.content)["triggerSource"] == "SCHEDULE"

    reported = json.loads(complete.calls[0].request.content)
    assert reported["status"] == "SUCCEEDED"
    assert reported["itemsScanned"] == 1
    assert reported["details"]["staleCount"] == 1


@respx.mock
def test_a_crash_is_recorded_as_failed_and_re_raised(settings):
    respx.post(f"{BASE}/api/v1/automation/runs").mock(
        return_value=httpx.Response(201, json=run_json(9))
    )
    complete = respx.post(f"{BASE}/api/v1/automation/runs/9/complete").mock(
        return_value=httpx.Response(200, json=run_json(9, status="FAILED"))
    )
    respx.get(f"{BASE}/api/v1/applications").mock(
        return_value=httpx.Response(500, json={"title": "Internal Server Error"})
    )

    with JobTrackerClient(settings) as client:
        with pytest.raises(ApiError):
            with record_run(client, JOB_NAME) as outcome:
                nudge_stale(client, settings, outcome, apply=False, now=NOW)

    reported = json.loads(complete.calls[0].request.content)
    assert reported["status"] == "FAILED"
    assert "ApiError" in reported["message"]


@respx.mock
def test_failing_to_report_does_not_replace_the_original_failure(settings):
    """If the API is what broke, completing the run breaks too. The first error wins."""
    respx.post(f"{BASE}/api/v1/automation/runs").mock(
        return_value=httpx.Response(201, json=run_json(9))
    )
    respx.post(f"{BASE}/api/v1/automation/runs/9/complete").mock(
        return_value=httpx.Response(503, json={"title": "Service Unavailable"})
    )

    with JobTrackerClient(settings) as client:
        with pytest.raises(ZeroDivisionError):
            with record_run(client, JOB_NAME):
                raise ZeroDivisionError("the real problem")
