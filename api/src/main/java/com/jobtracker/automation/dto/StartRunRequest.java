package com.jobtracker.automation.dto;

import com.jobtracker.automation.TriggerSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Announces that a job has begun. Sent before the work, not after, so a job that
 * crashes still leaves evidence it ran.
 */
public record StartRunRequest(

        @NotBlank
        @Size(max = 64)
        @Pattern(regexp = "[a-z][a-z0-9_]*",
                message = "must be lower_snake_case, e.g. nudge_stale")
        String jobName,

        /** Optional. Defaults to MANUAL. */
        TriggerSource triggerSource,

        /**
         * Optional. Defaults to now. The worker may send its own clock reading so
         * the recorded duration covers its whole run, not just the API round trips.
         */
        Instant startedAt
) {
}
