"""Exit codes and the failure messages a human actually sees.

A scheduler reads the exit code and nothing else, so the codes are part of the
contract: 0 ran, 1 the API refused or was unreachable, 2 you asked for something
incoherent.
"""

from __future__ import annotations

import httpx
import respx
from conftest import application_json, page_json, run_json

from jobtracker_automation.cli import main

BASE = "http://api.test"
ARGS = ["nudge-stale", "--api-base-url", BASE]


@respx.mock
def test_a_successful_run_exits_zero():
    respx.post(f"{BASE}/api/v1/automation/runs").mock(
        return_value=httpx.Response(201, json=run_json(1))
    )
    respx.post(f"{BASE}/api/v1/automation/runs/1/complete").mock(
        return_value=httpx.Response(200, json=run_json(1, status="SUCCEEDED"))
    )
    respx.get(f"{BASE}/api/v1/applications").mock(
        return_value=httpx.Response(
            200, json=page_json([application_json(1)], page=0, size=100, total=1)
        )
    )

    assert main(ARGS) == 0


@respx.mock
def test_an_unreachable_api_exits_one_without_a_traceback(caplog):
    """httpx raises its own ConnectError, which is not an OSError.

    That distinction is the reason this test exists: catching OSError looks
    right, compiles, passes every mocked test, and then dumps a forty-line
    traceback the first time the API is actually down.
    """
    respx.post(f"{BASE}/api/v1/automation/runs").mock(
        side_effect=httpx.ConnectError("connection refused")
    )

    assert main(ARGS) == 1
    assert "could not reach the API" in caplog.text


@respx.mock
def test_an_api_refusal_exits_one():
    respx.post(f"{BASE}/api/v1/automation/runs").mock(
        return_value=httpx.Response(400, json={"title": "Validation failed"})
    )

    assert main(ARGS) == 1


def test_apply_without_a_ghost_threshold_is_a_usage_error(caplog):
    """Refuse rather than run a no-op that looks like it did something."""
    assert main([*ARGS, "--apply"]) == 2
    assert "--ghost-after-days" in caplog.text


def test_an_incoherent_threshold_pair_is_a_usage_error():
    assert main([*ARGS, "--stale-after-days", "30", "--ghost-after-days", "7"]) == 2


def test_a_missing_service_key_exits_two_without_a_traceback(monkeypatch, caplog):
    """Configuration, not a refusal.

    Exit 2 rather than 1 so a scheduler can tell "you have not set this up" from
    "the API said no" - the first needs a human, the second may fix itself on
    the next run.

    Set to empty rather than deleted. Deleting the variable only unsets one of
    the two places Settings looks: a developer with a real automation/.env would
    still get a key from the file, and this test would quietly start asserting
    nothing. An environment variable outranks .env, so empty wins everywhere -
    and empty is what the server treats as unconfigured too.
    """
    monkeypatch.setenv("JOBTRACKER_SERVICE_KEY", "")

    assert main(ARGS) == 2
    assert "JOBTRACKER_SERVICE_KEY" in caplog.text
