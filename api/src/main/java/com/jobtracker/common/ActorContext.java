package com.jobtracker.common;

/**
 * Answers "who is making this call?" for the current request.
 *
 * <p>This is an interface with one implementation so that the service layer can
 * record an actor without knowing that HTTP headers exist. That matters more
 * than it looks: phase 5 replaces the implementation with one that reads an
 * authenticated principal, and no service method changes. The seam is the point.
 */
public interface ActorContext {

    /** Never null. Falls back to {@link Actor#SYSTEM} outside a request. */
    Actor current();

    /**
     * Which automation, when {@link #current()} is {@link Actor#AUTOMATION}.
     * Null for humans - naming a person is phase 5's job.
     */
    String detail();
}
