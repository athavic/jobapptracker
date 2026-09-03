package com.jobtracker.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "company")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The tenant this row belongs to.
     *
     * <p>A plain id, not a @ManyToOne to a Workspace entity. Nothing here needs
     * a workspace object - the column exists to be filtered on, and phase 5e
     * filters it in SQL policies that never see Java at all. Mapping the
     * association would add a table to the object graph purely so it could be
     * ignored.
     */
    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    /**
     * No longer globally unique: two workspaces both tracking Stripe is normal.
     * Uniqueness is now (workspace_id, lower(name)), which cannot be expressed
     * as a column annotation - see uq_company_workspace_name in V7.
     */
    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String website;

    @Column(name = "careers_url", length = 500)
    private String careersUrl;

    @Column(columnDefinition = "text")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** JPA requires a no-arg constructor. Keep it protected so app code uses the real one. */
    protected Company() {
    }

    public Company(Long workspaceId, String name) {
        this.workspaceId = workspaceId;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    public String getCareersUrl() {
        return careersUrl;
    }

    public void setCareersUrl(String careersUrl) {
        this.careersUrl = careersUrl;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
