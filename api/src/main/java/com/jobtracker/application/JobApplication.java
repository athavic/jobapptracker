package com.jobtracker.application;

import com.jobtracker.company.Company;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.math.BigDecimal;

@Entity
@Table(name = "job_application")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY so listing applications does not silently drag every company along.
     * When the company IS needed, fetch it deliberately (see ApplicationSpecs.fetchCompany).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    /**
     * The tenant, copied from the company rather than passed in.
     *
     * <p>Deriving it is what makes the two impossible to disagree. An
     * application and its company are joined on every read, so an application
     * filed under one workspace whose company sits in another is not a wrong
     * answer, it is a row that vanishes from both. The service resolves the
     * company within the current workspace already, so the tenant is decided in
     * exactly one place and everything downstream inherits it.
     */
    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    @Column(name = "role_title", nullable = false, length = 250)
    private String roleTitle;

    /**
     * STRING, never the default ORDINAL. With ORDINAL, reordering the enum above
     * would silently change the meaning of every row already in the table.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationStatus status = ApplicationStatus.SAVED;

    @Column(length = 64)
    private String source;

    @Column(name = "job_url", length = 1000)
    private String jobUrl;

    @Column(length = 200)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "remote_type", length = 16)
    private RemoteType remoteType;

    @Column(name = "salary_min", precision = 12, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 12, scale = 2)
    private BigDecimal salaryMax;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "salary_period", length = 16)
    private SalaryPeriod salaryPeriod;

    @Column(nullable = false)
    private Integer priority = 3;

    @Column(name = "resume_version", length = 100)
    private String resumeVersion;

    @Column(columnDefinition = "text")
    private String notes;

    /** Instant + TIMESTAMPTZ. UTC in the database, formatted only at the UI edge. */
    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(nullable = false)
    private boolean archived = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Optimistic locking. Hibernate adds {@code AND version = ?} to every UPDATE
     * of this row and increments the column; if no row matches, someone else
     * wrote first and the commit fails rather than overwriting them.
     *
     * <p>Optimistic rather than a {@code SELECT ... FOR UPDATE}: conflicts here
     * are rare - one person and one worker - and the pessimistic version would
     * make every read of every application pay for a collision that almost never
     * happens. This costs nothing until two writers actually meet.
     *
     * <p>No getter. Nothing in the application should read or reason about this;
     * it is a fact about the row, not about the job application. See
     * V5__optimistic_locking.sql for the race it closes.
     */
    @Version
    @Column(nullable = false)
    private long version;

    protected JobApplication() {
    }

    public JobApplication(Company company, String roleTitle, ApplicationStatus status) {
        this.company = company;
        this.workspaceId = company.getWorkspaceId();
        this.roleTitle = roleTitle;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getRoleTitle() {
        return roleTitle;
    }

    public void setRoleTitle(String roleTitle) {
        this.roleTitle = roleTitle;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    /**
     * Deliberately package-private: status changes go through
     * {@link ApplicationService#changeStatus} so the transition rules always run.
     */
    void setStatus(ApplicationStatus status) {
        this.status = status;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public RemoteType getRemoteType() {
        return remoteType;
    }

    public void setRemoteType(RemoteType remoteType) {
        this.remoteType = remoteType;
    }

    public BigDecimal getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(BigDecimal salaryMin) {
        this.salaryMin = salaryMin;
    }

    public BigDecimal getSalaryMax() {
        return salaryMax;
    }

    public void setSalaryMax(BigDecimal salaryMax) {
        this.salaryMax = salaryMax;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public SalaryPeriod getSalaryPeriod() {
        return salaryPeriod;
    }

    public void setSalaryPeriod(SalaryPeriod salaryPeriod) {
        this.salaryPeriod = salaryPeriod;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getResumeVersion() {
        return resumeVersion;
    }

    public void setResumeVersion(String resumeVersion) {
        this.resumeVersion = resumeVersion;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(Instant appliedAt) {
        this.appliedAt = appliedAt;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
