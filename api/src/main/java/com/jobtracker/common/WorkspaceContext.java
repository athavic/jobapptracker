package com.jobtracker.common;

import java.util.Optional;

/**
 * Answers "which workspace does this request belong to?".
 *
 * <p>The sibling of {@link ActorContext}, and an interface for the same reason:
 * the service layer scopes its writes without knowing where the answer comes
 * from. Today it comes from configuration, because there is no sign-in yet.
 * Phase 5c replaces the implementation with one that reads the authenticated
 * principal's membership, and no service method changes.
 *
 * <p>Note what this deliberately does NOT do: it does not filter reads. Phase 5b
 * makes every row belong to a workspace; phase 5d makes every query say so. Both
 * at once would mean debugging a schema change and an access-control change in
 * the same commit, and only one of those fails loudly.
 */
public interface WorkspaceContext {

    /** Never null. The workspace new rows are written into. */
    Long currentId();

    /**
     * The workspace a request may READ, or empty to read across all of them.
     *
     * <p>Separate from {@link #currentId()} because the two questions have
     * different answers for the Python worker: it has no workspace to write
     * into, and nudge_stale legitimately scans every one of them.
     *
     * <p>An Optional rather than a nullable Long, and that is the whole design.
     * Empty means "no filter", which is the most dangerous value in this
     * codebase - it has exactly one correct caller. Making it a value the reader
     * has to unwrap is what stops it being reached by a forgotten null check.
     */
    Optional<Long> readScope();
}
