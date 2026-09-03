package com.jobtracker.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The placeholder: every request belongs to the workspace named in configuration.
 *
 * <p>This exists because 5b and 5c are separate sittings. V7 makes workspace_id
 * NOT NULL, so something has to supply it, and the thing that will supply it -
 * an authenticated principal - does not exist until Google sign-in lands. The
 * alternative was a column DEFAULT in the database, which is worse: a default
 * keeps silently working after it has stopped being correct, whereas this class
 * is a file with "placeholder" in its name that somebody has to delete.
 *
 * <p>Delete it in 5c. The id it returns is the workspace V7 inserts for the
 * backfill, written explicitly there so this is a documented constant rather
 * than a guess about where a sequence starts.
 *
 * <p>Safe only because the API is unauthenticated and bound to localhost - the
 * same reason {@link HeaderActorContext} is allowed to trust its header. Once
 * there are two workspaces and a real user, a hardcoded tenant is a data-leak
 * bug, not a convenience.
 */
@Component
class FixedWorkspaceContext implements WorkspaceContext {

    private final Long workspaceId;

    FixedWorkspaceContext(@Value("${app.tenancy.bootstrap-workspace-id}") Long workspaceId) {
        this.workspaceId = workspaceId;
    }

    @Override
    public Long currentId() {
        return workspaceId;
    }
}
