package com.jobtracker.application.dto;

import com.jobtracker.application.ApplicationEventType;
import com.jobtracker.application.ApplicationStatus;
import com.jobtracker.common.Actor;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One line of an application's timeline.
 *
 * <p>Nullability here is meaningful rather than incidental, and the REQUIRED
 * markers are what let the TypeScript say so: id, type, actor and occurredAt
 * are true of every event, while the two statuses depend on the type. The React
 * code therefore has to narrow on type before reading them - which is exactly
 * the check the database also enforces.
 */
public record ApplicationEventResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ApplicationEventType type,

        /** Null unless type is STATUS_CHANGED. */
        ApplicationStatus fromStatus,

        /** Null when type is ARCHIVED. */
        ApplicationStatus toStatus,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Actor actor,

        /** The job name, when a worker did this. */
        String actorDetail,

        /** The reason given with a status change, if any. */
        String note,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant occurredAt
) {
}
