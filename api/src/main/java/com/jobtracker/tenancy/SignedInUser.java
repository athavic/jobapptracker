package com.jobtracker.tenancy;

/**
 * Who is signed in, and where their data lives.
 *
 * <p>Deliberately not the {@link AppUser} entity. This is what gets put in the
 * session and read on every subsequent request, and an entity in a session is a
 * detached object that goes stale the moment anything updates the row it came
 * from. A record of the two ids plus the details worth showing is enough.
 */
public record SignedInUser(Long userId,
                           Long workspaceId,
                           String email,
                           String displayName,
                           String avatarUrl) {
}
