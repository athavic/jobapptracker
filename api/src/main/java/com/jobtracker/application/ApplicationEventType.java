package com.jobtracker.application;

/**
 * What kind of thing happened.
 *
 * <p>Kept small on purpose. Every value here is something you would want to see
 * on a timeline as its own line; anything that would render as noise does not
 * belong. Field edits are the obvious omission - "salary changed" is real, but
 * a timeline of every keystroke-level correction buries the four events that
 * actually tell the story of an application.
 */
public enum ApplicationEventType {

    /** The application entered the system. Carries the status it started at. */
    CREATED,

    /** A legal move through the lifecycle. Carries both statuses. */
    STATUS_CHANGED,

    /** Filed away. Carries neither status - archiving does not move the lifecycle. */
    ARCHIVED
}
