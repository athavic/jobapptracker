package com.jobtracker.tenancy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, Long> {

    /**
     * Every workspace this person belongs to, oldest first.
     *
     * <p>Ordered so that "which workspace am I in?" has a stable answer while
     * there is no workspace switcher to ask with. Sorting by id rather than
     * joinedAt because two memberships created in the same transaction share a
     * timestamp, and an answer that changes between requests would be worse
     * than an arbitrary one.
     */
    List<WorkspaceMember> findByAppUserIdOrderByIdAsc(Long appUserId);

    /** Whether anyone at all has claimed this workspace. */
    boolean existsByWorkspaceId(Long workspaceId);
}
