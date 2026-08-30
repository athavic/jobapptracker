package com.jobtracker.application.dto;

import com.jobtracker.application.RemoteType;
import com.jobtracker.common.ValidationPatterns;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * PATCH semantics: every field is optional, and null means "leave this alone".
 *
 * <p>The tradeoff is that you cannot clear a field back to null through this
 * endpoint. That is a known limitation of naive PATCH and worth revisiting
 * later (JSON Merge Patch, or an explicit Optional wrapper) once it bites.
 *
 * <p>Status is NOT here on purpose - it moves through POST /{id}/status so the
 * transition rules cannot be bypassed.
 */
public record UpdateApplicationRequest(

        @Size(max = 200) String companyName,
        @Size(max = 250) String roleTitle,
        @Size(max = 64) String source,
        @Pattern(regexp = ValidationPatterns.HTTP_URL,
                message = "must be an absolute URL starting with http:// or https://")
        @Size(max = 1000) String jobUrl,
        @Size(max = 200) String location,

        RemoteType remoteType,

        @PositiveOrZero Integer salaryMin,
        @PositiveOrZero Integer salaryMax,

        @Size(min = 3, max = 3) String currency,

        @Min(1) @Max(5) Integer priority,

        @Size(max = 100) String resumeVersion,

        String notes,

        Instant appliedAt,

        Boolean archived
) {
}
