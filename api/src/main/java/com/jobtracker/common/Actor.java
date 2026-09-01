package com.jobtracker.common;

/**
 * Who caused a change.
 *
 * <p>Deliberately three values, not a user id. There is no authentication until
 * phase 5, so the only distinction the system can actually make is the one that
 * matters right now: was this a person at the dashboard, a Python job, or the
 * application itself? When real identities arrive, this enum stays and gains a
 * user alongside it - the question "was this a bot?" does not stop being useful
 * once you can also answer "which human?".
 */
public enum Actor {

    /** Someone at the UI, or anything that did not say otherwise. */
    HUMAN,

    /** A worker in {@code automation/}. The job name goes in actorDetail. */
    AUTOMATION,

    /** The application acting on its own - migrations, backfills, scheduled internals. */
    SYSTEM
}
