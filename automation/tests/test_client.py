from __future__ import annotations

import httpx
import pytest
import respx
from conftest import application_json, page_json

from jobtracker_automation.client import ApiError, JobTrackerClient
from jobtracker_automation.models import ApplicationStatus

BASE = "http://api.test"


@respx.mock
def test_iter_applications_walks_every_page(settings):
    """Paging is the client's problem, not each job's."""
    route = respx.get(f"{BASE}/api/v1/applications").mock(
        side_effect=[
            httpx.Response(
                200,
                json=page_json(
                    [application_json(1), application_json(2)], page=0, size=2, total=3
                ),
            ),
            httpx.Response(
                200, json=page_json([application_json(3)], page=1, size=2, total=3)
            ),
        ]
    )

    with JobTrackerClient(settings) as client:
        found = list(client.iter_applications())

    assert [a.id for a in found] == [1, 2, 3]
    assert route.call_count == 2
    # Pinned sort: paging over a mutable ordering can repeat or drop rows.
    assert route.calls[0].request.url.params["sort"] == "id,asc"


@respx.mock
def test_a_single_full_page_does_not_fetch_a_second(settings):
    """`last: true` ends the walk. Trusting the flag beats guessing from length."""
    route = respx.get(f"{BASE}/api/v1/applications").mock(
        return_value=httpx.Response(
            200,
            json=page_json(
                [application_json(1), application_json(2)], page=0, size=2, total=2
            ),
        )
    )

    with JobTrackerClient(settings) as client:
        assert len(list(client.iter_applications())) == 2

    assert route.call_count == 1


@respx.mock
def test_problem_detail_becomes_a_readable_error(settings):
    respx.post(f"{BASE}/api/v1/applications/7/status").mock(
        return_value=httpx.Response(
            409,
            json={
                "type": "about:blank",
                "title": "Illegal status transition",
                "status": 409,
                "detail": "Cannot move from OFFER to GHOSTED. Allowed from OFFER: [ACCEPTED, REJECTED, GHOSTED, WITHDRAWN]",
            },
        )
    )

    with JobTrackerClient(settings) as client:
        with pytest.raises(ApiError) as caught:
            client.change_status(7, ApplicationStatus.GHOSTED)

    error = caught.value
    assert error.is_conflict
    assert error.problem is not None
    assert "Cannot move from OFFER" in str(error)


@respx.mock
def test_an_unparseable_error_body_still_raises(settings):
    """A 502 from a proxy is not a problem detail. It is still a failure."""
    respx.get(f"{BASE}/api/v1/applications/7").mock(
        return_value=httpx.Response(502, text="<html>Bad Gateway</html>")
    )

    with JobTrackerClient(settings) as client:
        with pytest.raises(ApiError) as caught:
            client.get_application(7)

    assert caught.value.status_code == 502
    assert caught.value.problem is None


@respx.mock
def test_every_request_identifies_the_worker_as_a_bot(settings):
    """The header is the whole reason the events table can tell us apart.

    Asserted on a read as well as a write: it is set once on the session, so a
    future endpoint added to this client cannot forget it.
    """
    respx.get(f"{BASE}/api/v1/applications").mock(
        return_value=httpx.Response(200, json=page_json([application_json(1)], page=0, size=2, total=1))
    )
    route = respx.post(f"{BASE}/api/v1/applications/1/status").mock(
        return_value=httpx.Response(200, json=application_json(1, status="GHOSTED"))
    )

    with JobTrackerClient(settings, job_name="nudge_stale") as client:
        list(client.iter_applications())
        client.change_status(1, ApplicationStatus.GHOSTED, note="no reply")

    for call in respx.calls:
        assert call.request.headers["X-Actor"] == "AUTOMATION"
        assert call.request.headers["X-Actor-Detail"] == "nudge_stale"

    assert route.call_count == 1


@respx.mock
def test_a_client_with_no_job_name_still_admits_to_being_a_bot(settings):
    """Forgetting the name costs detail, never the distinction that matters."""
    respx.get(f"{BASE}/api/v1/applications/1").mock(
        return_value=httpx.Response(200, json=application_json(1))
    )

    with JobTrackerClient(settings) as client:
        client.get_application(1)

    request = respx.calls[0].request
    assert request.headers["X-Actor"] == "AUTOMATION"
    assert "X-Actor-Detail" not in request.headers
