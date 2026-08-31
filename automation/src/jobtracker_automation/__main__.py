"""Lets the package be run as ``python -m jobtracker_automation``."""

import sys

from .cli import main

sys.exit(main())
