package com.jobtracker.automation.dto;

import com.jobtracker.automation.AutomationRunStatus;
import com.jobtracker.common.FieldLimits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.Map;

/** Reports the outcome. The counterpart to {@link StartRunRequest}. */
public record CompleteRunRequest(

        /** SUCCEEDED or FAILED. RUNNING is rejected - completing means finishing. */
        @NotNull
        AutomationRunStatus status,

        @PositiveOrZero
        Integer itemsScanned,

        @PositiveOrZero
        Integer itemsAffected,

        /** One line a human can read in a dashboard cell. */
        @Size(max = FieldLimits.SHORT_NOTE) String message,

        /** Whatever the job wants to keep. Stored as jsonb; no fixed shape. */
        Map<String, Object> details,

        /** Optional. Defaults to now. */
        Instant finishedAt
) {
}
