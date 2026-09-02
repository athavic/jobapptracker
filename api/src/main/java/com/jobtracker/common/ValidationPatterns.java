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

    /**
     * At least one non-whitespace character.
     *
     * <p>This is {@code @NotBlank} for a PATCH field, and the difference is the
     * whole reason it exists. {@code @NotBlank} fails on null, but null is how
     * PATCH says "leave this alone" - so annotating an optional field with it
     * would make every partial update illegal unless it sent every field.
     * {@code @Pattern} skips null and checks everything else, which is exactly
     * the rule wanted: absent is fine, present-but-empty is not.
     *
     * <p>{@code (?s)} so that {@code .} also matches a newline. Without it a
     * value of "\n x" would be rejected, because the match must cover the whole
     * string.
     */
    public static final String NON_BLANK = "(?s).*\\S.*";

    private ValidationPatterns() {
    }
}
