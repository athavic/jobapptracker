"""Do the hand-written models still match the API?

The other tests use fixtures that *I* wrote, so they can only prove the worker
is consistent with itself. This module is the one that catches drift: it reads
the live OpenAPI document the Java side generates and compares it against the
models, field by field. It is the Python equivalent of what
``npm run generate:api`` plus ``tsc`` does for the front end - later and looser,
because nothing here is generated, but aimed at the same failure.

Skipped when the API is not running, so ``pytest`` still passes offline. Run it
for real before trusting a worker after any backend change:

    npm run dev:api          # in another terminal
    pytest tests/test_contract.py
"""

from __future__ import annotations

import os
from typing import Any

import httpx
import pytest

from jobtracker_automation.models import (
    Application,
    ApplicationStatus,
    AutomationRun,
    AutomationRunStatus,
    RemoteType,
    SalaryPeriod,
    TriggerSource,
)

API = os.environ.get("JOBTRACKER_API_BASE_URL", "http://localhost:8080").rstrip("/")


@pytest.fixture(scope="module")
def openapi() -> dict[str, Any]:
    try:
        response = httpx.get(f"{API}/v3/api-docs", timeout=5.0)
        response.raise_for_status()
    except (httpx.HTTPError, OSError) as unreachable:
        pytest.skip(f"API not running at {API} ({unreachable})")
    return response.json()


def schema(openapi: dict[str, Any], name: str) -> dict[str, Any]:
    schemas = openapi["components"]["schemas"]
    assert name in schemas, f"{name} is no longer in the OpenAPI document"
    return schemas[name]


def aliases(model: type) -> set[str]:
    return {
        field.alias or name for name, field in model.model_fields.items()
    }


def required_aliases(model: type) -> set[str]:
    return {
        field.alias or name
        for name, field in model.model_fields.items()
        if field.is_required()
    }


@pytest.mark.parametrize(
    ("model", "schema_name"),
    [
        (Application, "ApplicationResponse"),
        (AutomationRun, "AutomationRunResponse"),
    ],
)
def test_model_covers_every_field_the_api_sends(openapi, model, schema_name):
    """A field the API added and the model ignores is silent data loss."""
    published = set(schema(openapi, schema_name)["properties"])
    missing = published - aliases(model)
    assert not missing, (
        f"{model.__name__} is missing {sorted(missing)}, which "
        f"{schema_name} now sends. Add them, or decide explicitly to drop them."
    )


@pytest.mark.parametrize(
    ("model", "schema_name"),
    [
        (Application, "ApplicationResponse"),
        (AutomationRun, "AutomationRunResponse"),
    ],
)
def test_fields_the_api_guarantees_are_required_here_too(openapi, model, schema_name):
    """The mirror of @Schema(requiredMode = REQUIRED) on the Java side.

    A guaranteed field typed as optional in Python means every job that reads it
    carries a None check for a case that cannot happen.
    """
    guaranteed = set(schema(openapi, schema_name).get("required", []))
    optional_here = guaranteed - required_aliases(model)
    assert not optional_here, (
        f"{sorted(optional_here)} are always present per the API, "
        f"but optional on {model.__name__}"
    )


@pytest.mark.parametrize(
    ("enum", "schema_name", "property_name"),
    [
        (ApplicationStatus, "ApplicationResponse", "status"),
        (RemoteType, "ApplicationResponse", "remoteType"),
        (SalaryPeriod, "ApplicationResponse", "salaryPeriod"),
        (AutomationRunStatus, "AutomationRunResponse", "status"),
        (TriggerSource, "AutomationRunResponse", "triggerSource"),
    ],
)
def test_enums_match_exactly(openapi, enum, schema_name, property_name):
    """Both directions matter.

    A value the API can send and Python does not know is a hard validation
    failure mid-run. A value Python knows and the API rejects is a 400 waiting
    for whichever job sends it first.

    Enums are read out of the property that uses them rather than by name:
    springdoc inlines them into each schema instead of emitting a named
    component, so ``components.schemas.ApplicationStatus`` does not exist.
    """
    published = set(schema(openapi, schema_name)["properties"][property_name]["enum"])
    mirrored = {member.value for member in enum}
    assert published == mirrored


def test_automation_is_still_a_valid_actor(openapi):
    """The client hard-codes the X-Actor value it sends.

    The header itself is not in the OpenAPI document - it is read outside the
    controller signature - but the enum behind it is, via the event DTO. So this
    is the one place the hard-coded string can be checked against the server.
    Rename Actor.AUTOMATION in Java and every write the worker makes starts
    coming back 400; this fails first, and says why.
    """
    actor = schema(openapi, "ApplicationEventResponse")["properties"]["actor"]
    assert "AUTOMATION" in actor["enum"]


def test_the_event_dto_still_marks_actor_and_type_as_required(openapi):
    """The two fields the timeline cannot render without."""
    required = set(schema(openapi, "ApplicationEventResponse").get("required", []))
    assert {"id", "type", "actor", "occurredAt"} <= required
