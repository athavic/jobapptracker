package com.jobtracker.application;

import com.jobtracker.common.Actor;
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

import java.time.Instant;

/**
 * One thing that happened to one application.
 *
 * <p>Append-only: there are no setters, and nothing in the codebase updates a
 * row once written. That is not tidiness, it is the whole value of the table.
 * A history you can edit answers "what do we currently claim happened", which
 * is the question job_application already answers.
 *
 * <p>Instances come from the static factories rather than a general-purpose
 * constructor, so an event that says STATUS_CHANGED cannot be built without the
 * two statuses it changed between. The database asserts the same rules in
 * V4__application_event.sql; having them in both places means a bug is caught
 * at the nearest edge, and the schema stays correct even against psql.
 */
@Entity
@Table(name = "application_event")
public class ApplicationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * LAZY for the same reason JobApplication.company is: the timeline endpoint
     * already knows which application it asked about and must not issue a select
     * per event to rediscover it.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false, updatable = false)
    private JobApplication application;

    /**
     * Copied from the application, for the same reason JobApplication copies it
     * from the company: an event about an application in another workspace is
     * not a thing that can meaningfully exist.
     *
     * <p>Stored rather than reached through application_id because phase 5e
     * writes row-level security policies against this table, and a policy that
     * has to join to find its tenant is both slower and easier to get wrong.
     */
    @Column(name = "workspace_id", nullable = false, updatable = false)
    private Long workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private ApplicationEventType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32, updatable = false)
    private ApplicationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 32, updatable = false)
    private ApplicationStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Actor actor;

    @Column(name = "actor_detail", length = 64, updatable = false)
    private String actorDetail;

    @Column(columnDefinition = "text", updatable = false)
    private String note;

    /**
     * Set explicitly rather than by @CreationTimestamp. The column means "when
     * this happened", and those two only coincide for events written live.
     */
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected ApplicationEvent() {
    }

    private ApplicationEvent(JobApplication application,
                             ApplicationEventType type,
                             ApplicationStatus fromStatus,
                             ApplicationStatus toStatus,
                             Actor actor,
                             String actorDetail,
                             String note) {
        this.application = application;
        this.workspaceId = application.getWorkspaceId();
        this.type = type;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actor = actor;
        this.actorDetail = actorDetail;
        this.note = note;
        this.occurredAt = Instant.now();
    }

    static ApplicationEvent created(JobApplication application,
                                    ApplicationStatus initialStatus,
                                    Actor actor,
                                    String actorDetail) {
        return new ApplicationEvent(application, ApplicationEventType.CREATED,
                null, initialStatus, actor, actorDetail, null);
    }

    static ApplicationEvent statusChanged(JobApplication application,
                                          ApplicationStatus from,
                                          ApplicationStatus to,
                                          Actor actor,
                                          String actorDetail,
                                          String note) {
        return new ApplicationEvent(application, ApplicationEventType.STATUS_CHANGED,
                from, to, actor, actorDetail, note);
    }

    static ApplicationEvent archived(JobApplication application,
                                     Actor actor,
                                     String actorDetail) {
        return new ApplicationEvent(application, ApplicationEventType.ARCHIVED,
                null, null, actor, actorDetail, null);
    }

    public Long getId() {
        return id;
    }

    public JobApplication getApplication() {
        return application;
    }

    public Long getWorkspaceId() {
        return workspaceId;
    }

    public ApplicationEventType getType() {
        return type;
    }

    public ApplicationStatus getFromStatus() {
        return fromStatus;
    }

    public ApplicationStatus getToStatus() {
        return toStatus;
    }

    public Actor getActor() {
        return actor;
    }

    public String getActorDetail() {
        return actorDetail;
    }

    public String getNote() {
        return note;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
