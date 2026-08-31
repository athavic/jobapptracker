package com.jobtracker.automation;

/** Where a single execution of a job got to. */
public enum AutomationRunStatus {

    /** Started, not yet reported back. A row stuck here means the job died. */
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean isFinished() {
        return this != RUNNING;
    }
}
