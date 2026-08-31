"""The decision, tested without a network in sight.

Every case here is a date-arithmetic or policy question, and none of them needs
HTTP to ask. Keeping ``select_stale`` pure is what makes that possible.
"""

from __future__ import annotations

from conftest import NOW, application_json

from jobtracker_automation.jobs.nudge_stale import select_stale
from jobtracker_automation.models import Application


def app(**kwargs) -> Application:
    return Application.model_validate(application_json(kwargs.pop("id", 1), **kwargs))


def ids(result) -> list[int]:
    return [item.application.id for item in result]


def test_quiet_longer_than_the_threshold_is_stale():
    result = select_stale([app(id=1, days_quiet=20)], now=NOW, stale_after_days=14)
    assert ids(result) == [1]
    assert result[0].days_since_movement == 20


def test_the_threshold_itself_counts_as_stale():
    """A boundary worth pinning down: >= not >, so "14 days" means 14 days."""
    assert ids(select_stale([app(id=1, days_quiet=14)], now=NOW, stale_after_days=14)) == [1]
    assert select_stale([app(id=1, days_quiet=13)], now=NOW, stale_after_days=14) == []


def test_terminal_applications_are_left_alone():
    """No status list is consulted - an empty allowedNextStatuses is what terminal means."""
    rejected = app(id=1, status="REJECTED", allowed_next=[], days_quiet=99)
    assert select_stale([rejected], now=NOW, stale_after_days=14) == []


def test_never_applied_is_somebody_elses_job():
    saved = app(id=1, status="SAVED", applied=False, days_quiet=99)
    assert select_stale([saved], now=NOW, stale_after_days=14) == []


def test_archived_applications_are_ignored():
    archived = app(id=1, archived=True, days_quiet=99)
    assert select_stale([archived], now=NOW, stale_after_days=14) == []


def test_longest_silence_comes_first():
    result = select_stale(
        [app(id=1, days_quiet=20), app(id=2, days_quiet=60), app(id=3, days_quiet=30)],
        now=NOW,
        stale_after_days=14,
    )
    assert ids(result) == [2, 3, 1]
