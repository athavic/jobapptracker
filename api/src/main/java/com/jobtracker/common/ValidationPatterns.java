package com.jobtracker.common;

/**
 * Regexes shared between request DTOs.
 *
 * <p>Annotation values have to be compile-time constants, so these live here
 * rather than being duplicated as literals across every record that needs them.
 */
public final class ValidationPatterns {

    /**
     * An absolute http(s) URL, or blank.
     *
     * <p>Blank is permitted because a client clearing a field naturally sends
     * "" rather than omitting it; the service turns that into a real null so
     * the database never stores an empty string. What this rejects is the case
     * that actually caused trouble - a bare "example.com", which the browser
     * would treat as a path relative to the app itself.
     */
    public static final String HTTP_URL = "^\\s*$|^https?://\\S+$";

    private ValidationPatterns() {
    }
}
