package com.jobtracker.tenancy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Turns a Google identity into a row in app_user and a workspace to work in.
 *
 * <p>Runs once per sign-in, inside one transaction: a user created without a
 * workspace could not write anything, and a membership created for a user that
 * failed to save would point at nobody.
 */
@Service
public class SignInService {

    private final AppUserRepository users;
    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final Long bootstrapWorkspaceId;

    public SignInService(AppUserRepository users,
                         WorkspaceRepository workspaces,
                         WorkspaceMemberRepository members,
                         @Value("${app.tenancy.bootstrap-workspace-id}") Long bootstrapWorkspaceId) {
        this.users = users;
        this.workspaces = workspaces;
        this.members = members;
        this.bootstrapWorkspaceId = bootstrapWorkspaceId;
    }

    @Transactional
    public SignedInUser signIn(String googleSub, String email, String displayName, String avatarUrl) {
        AppUser user = users.findByGoogleSub(googleSub)
                .orElseGet(() -> users.save(new AppUser(googleSub, email)));

        // Every sign-in, not just the first. Names and avatars change, and the
        // stored copy is what the UI shows - so it is refreshed from the token
        // rather than left as a snapshot of whoever this person was in 2026.
        user.refreshProfile(email, displayName, avatarUrl);

        List<WorkspaceMember> memberships = members.findByAppUserIdOrderByIdAsc(user.getId());
        Long workspaceId = memberships.isEmpty()
                ? claimAWorkspace(user)
                : memberships.get(0).getWorkspaceId();

        return new SignedInUser(
                user.getId(), workspaceId, user.getEmail(),
                user.getDisplayName(), user.getAvatarUrl());
    }

    /**
     * Where a brand new person ends up.
     *
     * <p>The first person to sign in adopts the workspace V7 created, because
     * that workspace already holds every application that existed before there
     * was any such thing as a user - leaving it unclaimed would mean your own
     * data was sitting in a workspace you are not a member of. Everyone after
     * that gets one of their own, which is the ordinary case forever after.
     *
     * <p>The lock is what makes "already claimed?" a safe question to ask; see
     * {@link WorkspaceRepository#findWithLockById}.
     */
    private Long claimAWorkspace(AppUser user) {
        boolean bootstrapIsFree = workspaces.findWithLockById(bootstrapWorkspaceId)
                .filter(workspace -> !members.existsByWorkspaceId(workspace.getId()))
                .isPresent();

        Long workspaceId = bootstrapIsFree
                ? bootstrapWorkspaceId
                : workspaces.save(new Workspace(defaultWorkspaceName(user))).getId();

        // OWNER, and there is nobody else to be one. V6 records why "at least
        // one OWNER" cannot be a database constraint; this is the other half of
        // that rule - a workspace is never created without one.
        members.save(new WorkspaceMember(workspaceId, user.getId(), WorkspaceRole.OWNER));
        return workspaceId;
    }

    /** Falls back to the address when Google gives us no name to use. */
    private static String defaultWorkspaceName(AppUser user) {
        String who = user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName()
                : user.getEmail();
        String name = who + "'s workspace";
        return name.length() > 200 ? name.substring(0, 200) : name;
    }
}
