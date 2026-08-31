"""One module per job. A job is a function that takes a client and settings."""

from .nudge_stale import nudge_stale

__all__ = ["nudge_stale"]
