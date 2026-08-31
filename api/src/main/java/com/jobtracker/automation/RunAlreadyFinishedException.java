package com.jobtracker.automation;

/** Completing a run that already has an outcome. Maps to 409. */
public class RunAlreadyFinishedException extends RuntimeException {

    public RunAlreadyFinishedException(String message) {
        super(message);
    }
}
