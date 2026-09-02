package com.jobtracker.common;

/**
 * Maximum lengths for the free-text fields that are backed by {@code TEXT}
 * rather than a {@code VARCHAR(n)}.
 *
 * <p>Every other string field on a request DTO is bounded by the column it
 * lands in, and its {@code @Size} simply mirrors that number. These are the
 * ones with no column to mirror: {@code TEXT} in PostgreSQL has no length
 * limit, so without a bound here they accept whatever the caller sends.
 *
 * <p>Chosen as "far larger than any real value, small enough to be a bound".
 * The point is not to police how much someone writes about a job; it is that
 * an unbounded write endpoint on the public internet is a free amplification
 * primitive, and the limit should be a decision rather than an accident.
 */
public final class FieldLimits {

    /** Everything you might want to remember about one application. */
    public static final int NOTES = 10_000;

    /** One line of reasoning attached to a single event or run. */
    public static final int SHORT_NOTE = 2_000;

    private FieldLimits() {
    }
}
