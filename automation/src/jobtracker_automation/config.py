"""Settings, resolved once at startup.

pydantic-settings gives the same thing the Spring side gets from application.yml:
values that come from the environment, are validated on load, and fail loudly at
startup rather than as an AttributeError somewhere deep in a job.
"""

from __future__ import annotations

from pathlib import Path

from pydantic import Field, HttpUrl, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

#: automation/.env, found relative to this file rather than to the working
#: directory - the job must behave the same whether it is started from the repo
#: root, from automation/, or by a scheduler with no working directory to speak
#: of. A .env in the current directory still wins, for one-off overrides.
_PACKAGE_ENV = Path(__file__).resolve().parents[2] / ".env"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="JOBTRACKER_",
        env_file=(_PACKAGE_ENV, ".env"),
        env_file_encoding="utf-8",
        # An unknown JOBTRACKER_* variable is almost always a typo in a .env
        # file. Better to refuse to start than to silently use the default.
        extra="forbid",
    )

    api_base_url: HttpUrl = Field(default=HttpUrl("http://localhost:8080"))

    timeout_seconds: float = Field(default=10.0, gt=0)

    #: Shared secret the API requires on every request from phase 5c on. No
    #: default, because a default would be a credential in source control and a
    #: worker that appears configured while being unable to authenticate. Must
    #: match app.automation.service-key on the server side, which reads it from
    #: AUTOMATION_SERVICE_KEY in the repository root .env.
    #:
    #: This key acts across every workspace, so it is the one value in this
    #: project that must never reach a browser.
    service_key: str | None = Field(default=None)

    #: Rows per page when walking a list endpoint. Large enough that a few
    #: hundred applications is one or two requests, small enough that a slow
    #: response never holds a huge result set in memory on either side.
    page_size: int = Field(default=100, ge=1, le=200)

    #: Days without movement before nudge_stale reports an application.
    stale_after_days: int = Field(default=14, ge=1)

    #: Days without movement before nudge_stale would call it ghosted. None
    #: means never - ghosting is opt-in, and only ever happens with --apply.
    ghost_after_days: int | None = Field(default=None, ge=1)

    @field_validator("ghost_after_days")
    @classmethod
    def ghost_threshold_must_exceed_stale(
        cls, value: int | None, info
    ) -> int | None:
        """Ghosting sooner than nudging would be incoherent.

        Validating the relationship here, rather than inside the job, means a
        bad .env fails at import time with a readable message instead of
        producing a run whose numbers quietly do not add up.
        """
        stale = info.data.get("stale_after_days")
        if value is not None and stale is not None and value < stale:
            raise ValueError(
                f"ghost_after_days ({value}) must be >= stale_after_days ({stale})"
            )
        return value

    @property
    def base_url(self) -> str:
        """httpx wants a plain string, and without the trailing slash its URL
        joining drops the last path segment. Normalise in one place."""
        return str(self.api_base_url).rstrip("/")


def load_settings(**overrides: object) -> Settings:
    """Read settings, letting explicit arguments (i.e. CLI flags) win.

    Precedence ends up as: CLI flag > environment variable > .env file > default,
    which is the order of least surprise.
    """
    supplied = {key: value for key, value in overrides.items() if value is not None}
    return Settings(**supplied)  # type: ignore[arg-type]
