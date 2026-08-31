"""Automation workers for the job tracker.

Everything here is a client of the Spring API. Nothing in this package opens a
database connection, and that restriction is the whole design: the lifecycle
rules, the validation and the audit trail live in exactly one place, so a job
cannot put the system into a state the API would have refused.
"""

__all__ = ["__version__"]

__version__ = "0.1.0"
