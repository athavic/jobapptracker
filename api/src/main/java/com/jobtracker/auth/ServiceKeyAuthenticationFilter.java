package com.jobtracker.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Lets the Python worker in, since it has no browser and therefore no session.
 *
 * <p>A deliberately small piece of phase 5f brought forward. 5c locks the API,
 * and without something like this nudge_stale - a working feature with its own
 * contract test - would stay dead through 5c, 5d and 5e. 5f replaces it with
 * the real thing: rotation, per-job identity, and the boundary written down.
 * What is here is the minimum that keeps the worker alive without pretending to
 * be more.
 *
 * <p>What it is NOT: a user. The service principal holds ROLE_SERVICE and no
 * workspace, so anything that needs to know whose data it is touching fails
 * rather than guessing - see AuthenticatedWorkspaceContext. The worker changes
 * applications it was given the ids of; it never creates one.
 */
public class ServiceKeyAuthenticationFilter extends OncePerRequestFilter {

    /** Also the marker CSRF uses to recognise a non-browser caller. */
    public static final String SERVICE_KEY_HEADER = "X-Service-Key";

    public static final String ROLE_SERVICE = "ROLE_SERVICE";

    private static final Logger log = LoggerFactory.getLogger(ServiceKeyAuthenticationFilter.class);

    private final byte[] expectedKey;

    public ServiceKeyAuthenticationFilter(String configuredKey) {
        this.expectedKey = configuredKey == null || configuredKey.isBlank()
                ? null
                : configuredKey.getBytes(StandardCharsets.UTF_8);

        if (this.expectedKey == null) {
            log.warn("No app.automation.service-key configured: the Python worker cannot "
                    + "authenticate and its requests will be rejected. Set AUTOMATION_SERVICE_KEY "
                    + "in .env to enable it.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String presented = request.getHeader(SERVICE_KEY_HEADER);

        // Fails closed: with no key configured, no key can ever match. An unset
        // property must not quietly become "authentication optional".
        if (expectedKey != null && presented != null && matches(presented)) {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                            "automation", null,
                            List.of(new SimpleGrantedAuthority(ROLE_SERVICE))));
        }

        // A wrong key is not rejected here. It simply authenticates nothing, and
        // the authorization rules then produce the same 401 as no key at all -
        // so a bad key and a missing one look identical from outside.
        chain.doFilter(request, response);
    }

    /**
     * Constant-time comparison. String.equals returns as soon as two bytes
     * differ, which leaks how much of a guess was correct; over enough requests
     * that is enough to rebuild the key one character at a time.
     */
    private boolean matches(String presented) {
        return MessageDigest.isEqual(presented.getBytes(StandardCharsets.UTF_8), expectedKey);
    }
}
