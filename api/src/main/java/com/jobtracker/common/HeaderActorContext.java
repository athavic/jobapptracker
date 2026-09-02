package com.jobtracker.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.Locale;

/**
 * Reads the actor from request headers.
 *
 * <p>This trusts the caller completely, which is only acceptable because there
 * is nothing yet to protect: the API is unauthenticated and bound to localhost,
 * so a client that lies about being a bot has gained nothing it could not
 * already do. Do not carry this forward past phase 5 - once there is a real
 * principal, a self-declared identity is a privilege-escalation bug, not a
 * convenience.
 *
 * <p>Headers rather than a body field so that a caller declares itself once, at
 * construction, instead of every write DTO growing a field the browser must
 * remember to populate.
 */
@Component
class HeaderActorContext implements ActorContext {

    static final String ACTOR_HEADER = "X-Actor";
    static final String DETAIL_HEADER = "X-Actor-Detail";

    /** Matches application_event.actor_detail; longer is a caller bug, not truncatable data. */
    private static final int MAX_DETAIL_LENGTH = 64;

    @Override
    public Actor current() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            // No request bound: an internal or scheduled call, not a person.
            return Actor.SYSTEM;
        }

        String raw = request.getHeader(ACTOR_HEADER);
        if (raw == null || raw.isBlank()) {
            // The browser sends no header. Anything at the UI is a human.
            return Actor.HUMAN;
        }

        try {
            return Actor.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            // Not defaulted to HUMAN on purpose. A typo in a worker's header
            // would then file every one of its writes under "a person did this",
            // and the actor column would be quietly wrong exactly where it is
            // most load-bearing. A 400 is noisy; wrong provenance is worse.
            throw new BusinessRuleException(
                    ACTOR_HEADER + " must be one of " + Arrays.toString(Actor.values())
                            + " (got '" + raw + "')");
        }
    }

    @Override
    public String detail() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }

        String raw = request.getHeader(DETAIL_HEADER);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String trimmed = raw.trim();
        if (trimmed.length() > MAX_DETAIL_LENGTH) {
            throw new BusinessRuleException(
                    DETAIL_HEADER + " must be at most " + MAX_DETAIL_LENGTH + " characters");
        }
        return trimmed;
    }

    /**
     * Null when nothing is bound to the thread, which is how a non-HTTP caller
     * is recognised without the service layer having to say so.
     */
    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest()
                : null;
    }
}
