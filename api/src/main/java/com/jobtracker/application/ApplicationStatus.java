package com.jobtracker.application;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The lifecycle of an application, plus the rules for moving through it.
 *
 * <p>Keeping the legal transitions here (rather than trusting whatever the caller
 * sends) is the reason the API is worth having: the React form and the Python
 * email job both go through this same check.
 */
public enum ApplicationStatus {

    DISCOVERED,
    SAVED,
    APPLIED,
    SCREEN,
    INTERVIEW,
    OFFER,
    ACCEPTED,
    REJECTED,
    GHOSTED,
    WITHDRAWN;

    /** Statuses an application never leaves. */
    private static final Set<ApplicationStatus> TERMINAL =
            Collections.unmodifiableSet(EnumSet.of(ACCEPTED, REJECTED, GHOSTED, WITHDRAWN));

    /** Ways an active application can end. Reachable from any active status. */
    private static final Set<ApplicationStatus> EXITS =
            Collections.unmodifiableSet(EnumSet.of(REJECTED, GHOSTED, WITHDRAWN));

    private static final Map<ApplicationStatus, Set<ApplicationStatus>> ALLOWED =
            new EnumMap<>(ApplicationStatus.class);

    static {
        ALLOWED.put(DISCOVERED, union(EnumSet.of(SAVED, APPLIED), EnumSet.of(WITHDRAWN)));
        ALLOWED.put(SAVED,      union(EnumSet.of(APPLIED), EnumSet.of(WITHDRAWN)));
        ALLOWED.put(APPLIED,    union(EnumSet.of(SCREEN, INTERVIEW, OFFER), EXITS));
        ALLOWED.put(SCREEN,     union(EnumSet.of(INTERVIEW, OFFER), EXITS));
        ALLOWED.put(INTERVIEW,  union(EnumSet.of(OFFER), EXITS));
        ALLOWED.put(OFFER,      union(EnumSet.of(ACCEPTED), EXITS));
        for (ApplicationStatus terminal : TERMINAL) {
            ALLOWED.put(terminal, Collections.unmodifiableSet(EnumSet.noneOf(ApplicationStatus.class)));
        }
    }

    private static Set<ApplicationStatus> union(Set<ApplicationStatus> a, Set<ApplicationStatus> b) {
        EnumSet<ApplicationStatus> combined = EnumSet.copyOf(a);
        combined.addAll(b);
        return Collections.unmodifiableSet(combined);
    }

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** Statuses this one can move to. Handy for the UI: render only the legal buttons. */
    public Set<ApplicationStatus> allowedNext() {
        return ALLOWED.getOrDefault(this, Set.of());
    }

    /** Re-setting the same status is a no-op, not an error. */
    public boolean canTransitionTo(ApplicationStatus next) {
        return next == this || allowedNext().contains(next);
    }
}
