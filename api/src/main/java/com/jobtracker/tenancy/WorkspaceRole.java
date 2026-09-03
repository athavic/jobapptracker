package com.jobtracker.tenancy;

/**
 * What a member may do in a workspace.
 *
 * <p>Three values, kept small until a fourth earns itself. Adding one later is
 * an ALTER on a CHECK constraint with no backfill; the expensive part is that
 * every authorization decision in the codebase grows another answer, and that
 * cost is the same whenever it happens. So there is no deadline, and no reason
 * to carry a role nothing has a rule for.
 *
 * <p>Stored as text with a matching CHECK in V6, never as an ordinal - see the
 * note on {@code ck_workspace_member_role}.
 */
public enum WorkspaceRole {

    /** Can remove members and delete the workspace. */
    OWNER,

    /** Can change applications, but not the membership list. */
    MEMBER,

    /** Read only. */
    VIEWER
}
