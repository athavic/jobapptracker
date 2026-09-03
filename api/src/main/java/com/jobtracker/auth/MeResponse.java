package com.jobtracker.auth;

/**
 * The signed-in user, as the UI needs them.
 *
 * <p>Deliberately not the Google sub. It identifies the account permanently and
 * the browser has no use for it, so it stays server-side; leaking a stable
 * cross-service identifier into a page is how it ends up in analytics.
 */
public record MeResponse(Long userId,
                         Long workspaceId,
                         String email,
                         String displayName,
                         String avatarUrl) {
}
