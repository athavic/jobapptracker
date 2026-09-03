package com.jobtracker.auth;

import com.jobtracker.common.Actor;
import com.jobtracker.common.ActorContext;
import com.jobtracker.common.BusinessRuleException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Who is acting, taken from the authenticated principal rather than a header.
 *
 * <p>The replacement HeaderActorContext promised in its own javadoc. That class
 * read X-Actor and believed it, which was acceptable only while there was
 * nothing to protect: a browser could call itself AUTOMATION, and the
 * provenance column - the one column whose entire job is to be trustworthy -
 * would record whatever the caller preferred. The answer now comes from how the
 * request authenticated, which the caller cannot choose.
 *
 * <p>X-Actor-Detail survives, because "which job" is genuinely information only
 * the worker has. It is trusted now for a different reason: only a caller
 * holding the service key can set it in a way that reaches an event row.
 */
@Component
class PrincipalActorContext implements ActorContext {

    static final String DETAIL_HEADER = "X-Actor-Detail";

    /** Matches application_event.actor_detail; longer is a caller bug, not truncatable data. */
    private static final int MAX_DETAIL_LENGTH = 64;

    @Override
    public Actor current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            // No principal at all: a scheduled or internal call, not a person.
            return Actor.SYSTEM;
        }
        if (authentication.getPrincipal() instanceof WorkspaceUser) {
            return Actor.HUMAN;
        }
        if (hasRole(authentication, ServiceKeyAuthenticationFilter.ROLE_SERVICE)) {
            return Actor.AUTOMATION;
        }
        return Actor.SYSTEM;
    }

    @Override
    public String detail() {
        // Only meaningful for the worker. A signed-in person naming a job would
        // put a machine name on a human change, so the header is ignored unless
        // the caller actually is a machine.
        if (current() != Actor.AUTOMATION) {
            return null;
        }

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

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> role.equals(granted.getAuthority()));
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs
                ? attrs.getRequest()
                : null;
    }
}
