package com.jobtracker.automation.dto;

import com.jobtracker.automation.AutomationRunStatus;
import com.jobtracker.automation.TriggerSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * As always, the required markers are what keep the generated TypeScript and the
 * generated-by-hand pydantic models from treating everything as optional.
 */
public record AutomationRunResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String jobName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        AutomationRunStatus status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        TriggerSource triggerSource,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant startedAt,

        /** Null exactly while the run is RUNNING. */
        Instant finishedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer itemsScanned,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer itemsAffected,

        String message,

        Map<String, Object> details
) {
}
