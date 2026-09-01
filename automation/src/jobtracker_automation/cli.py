"""Command line entry point.

argparse rather than click or typer: one dependency fewer, and the CLI is small
enough that the hand-written version is shorter than the docs for the alternative.
"""

from __future__ import annotations

import argparse
import logging
import sys

import httpx

from .client import ApiError, JobTrackerClient
from .config import Settings, load_settings
from .jobs import nudge_stale as run_nudge_stale
from .jobs.nudge_stale import JOB_NAME as NUDGE_STALE
from .models import TriggerSource
from .runs import record_run


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="jobtracker-automation",
        description="Automation jobs for the job application tracker.",
    )
    parser.add_argument("-v", "--verbose", action="store_true", help="debug logging")

    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--api-base-url", help="override JOBTRACKER_API_BASE_URL")
    common.add_argument(
        "--trigger",
        choices=[source.value.lower() for source in TriggerSource],
        default=TriggerSource.MANUAL.value.lower(),
        help="who asked for this run (recorded on the run row)",
    )
    common.add_argument(
        "--no-record",
        action="store_true",
        help="do not write an automation_run row (local experimentation)",
    )

    subparsers = parser.add_subparsers(dest="command", required=True)

    # The command is spelled with a hyphen and the job with an underscore. The
    # underscore version is the identifier stored in automation_run.job_name,
    # which the API validates as lower_snake_case.
    nudge = subparsers.add_parser(
        NUDGE_STALE.replace("_", "-"),
        parents=[common],
        help="report applications with no movement in N days",
    )
    nudge.add_argument("--stale-after-days", type=int)
    nudge.add_argument(
        "--ghost-after-days",
        type=int,
        help="silence after which an application counts as ghosted",
    )
    nudge.add_argument(
        "--apply",
        action="store_true",
        help="actually move the long-dead ones to GHOSTED (default: report only)",
    )

    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)-7s %(name)s | %(message)s",
        datefmt="%H:%M:%S",
    )
    log = logging.getLogger("jobtracker_automation")

    try:
        settings = load_settings(
            api_base_url=args.api_base_url,
            stale_after_days=getattr(args, "stale_after_days", None),
            ghost_after_days=getattr(args, "ghost_after_days", None),
        )
    except ValueError as bad_config:
        # Configuration errors are the user's typo, not a crash worth a
        # traceback. Say what is wrong and exit.
        log.error("bad configuration: %s", bad_config)
        return 2

    if args.apply and settings.ghost_after_days is None:
        log.error("--apply does nothing without --ghost-after-days")
        return 2

    trigger = TriggerSource(args.trigger.upper())

    try:
        return _dispatch(args, settings, trigger)
    except ApiError as error:
        log.error("%s", error)
        return 1
    except httpx.HTTPError as error:
        # Connection refused, DNS failure, read timeout. httpx wraps all of
        # these in its own exception hierarchy - they are NOT OSError, which is
        # the obvious wrong guess - and they deserve their own message because
        # the fix is "start the API", not "read a stack trace".
        log.error("could not reach the API at %s: %s", settings.base_url, error)
        return 1


def _dispatch(
    args: argparse.Namespace, settings: Settings, trigger: TriggerSource
) -> int:
    # One entry per job. The dispatch is explicit rather than clever so that
    # adding a second job is an obvious edit in an obvious place.
    if args.command != NUDGE_STALE.replace("_", "-"):
        raise AssertionError(f"unhandled command {args.command!r}")

    with JobTrackerClient(settings, job_name=NUDGE_STALE) as client:
        with record_run(
            client, NUDGE_STALE, trigger=trigger, enabled=not args.no_record
        ) as outcome:
            run_nudge_stale(client, settings, outcome, apply=args.apply)
    return 0


if __name__ == "__main__":
    sys.exit(main())
