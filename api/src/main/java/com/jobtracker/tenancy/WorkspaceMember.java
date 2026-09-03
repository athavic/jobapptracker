package com.jobtracker.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * One person's place in one workspace.
 *
 * <p>Both sides are stored as plain ids rather than {@code @ManyToOne}
 * associations. This table is only ever queried by id - "which workspaces is
 * this user in", "does this workspace have any members" - and never navigated
 * from, so mapping the associations would add two entities to the object graph
 * purely so they could be ignored. Same reasoning as JobApplication.workspaceId.
 */
@Entity
@Table(name = "workspace_member")
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    @Column(name = "app_user_id", nullable = false, updatable = false)
    private Long appUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkspaceRole role;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkspaceMember() {
    }

    public WorkspaceMember(Long workspaceId, Long appUserId, WorkspaceRole role) {
        this.workspaceId = workspaceId;
        this.appUserId = appUserId;
        this.role = role;
        this.joinedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public Long getAppUserId() {
        return appUserId;
    }

    public WorkspaceRole getRole() {
        return role;
    }
}
