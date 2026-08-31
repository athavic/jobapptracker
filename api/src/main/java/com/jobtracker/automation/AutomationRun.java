package com.jobtracker.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "automation_run")
public class AutomationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 64)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AutomationRunStatus status = AutomationRunStatus.RUNNING;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 32)
    private TriggerSource triggerSource = TriggerSource.MANUAL;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "items_scanned", nullable = false)
    private int itemsScanned;

    @Column(name = "items_affected", nullable = false)
    private int itemsAffected;

    @Column(columnDefinition = "text")
    private String message;

    /**
     * Free-form JSON, deliberately untyped on this side.
     *
     * <p>Each job decides what belongs in here, and changing that shape must not
     * require a migration - which is exactly what jsonb buys. @JdbcTypeCode(JSON)
     * is what makes Hibernate serialize the map through Jackson into the jsonb
     * column rather than trying to persist a Map as a relation.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AutomationRun() {
    }

    public AutomationRun(String jobName, TriggerSource triggerSource, Instant startedAt) {
        this.jobName = jobName;
        this.triggerSource = triggerSource;
        this.startedAt = startedAt;
    }

    /**
     * The only way a run leaves RUNNING. Kept on the entity so the invariant the
     * database also checks - finished exactly when finishedAt is set - cannot be
     * broken by a caller setting one and forgetting the other.
     */
    void finish(AutomationRunStatus outcome,
                Instant finishedAt,
                int itemsScanned,
                int itemsAffected,
                String message,
                Map<String, Object> details) {

        this.status = outcome;
        this.finishedAt = finishedAt;
        this.itemsScanned = itemsScanned;
        this.itemsAffected = itemsAffected;
        this.message = message;
        this.details = details;
    }

    public Long getId() {
        return id;
    }

    public String getJobName() {
        return jobName;
    }

    public AutomationRunStatus getStatus() {
        return status;
    }

    public TriggerSource getTriggerSource() {
        return triggerSource;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getItemsScanned() {
        return itemsScanned;
    }

    public int getItemsAffected() {
        return itemsAffected;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
