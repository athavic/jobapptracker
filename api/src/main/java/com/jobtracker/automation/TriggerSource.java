package com.jobtracker.automation;

/** Who asked for this run. Useful when a scheduled run and a manual one disagree. */
public enum TriggerSource {
    MANUAL,
    SCHEDULE
}
