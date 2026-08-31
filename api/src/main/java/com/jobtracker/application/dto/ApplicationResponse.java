package com.jobtracker.application.dto;

import com.jobtracker.application.ApplicationStatus;
import com.jobtracker.application.RemoteType;
import com.jobtracker.application.SalaryPeriod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Set;

/**
 * What the API returns. Separate from the entity on purpose: renaming a column
 * should never be a breaking API change, and lazily-loaded JPA relations must
 * never reach the JSON serializer.
 *
 * <p>The {@code @Schema(requiredMode = REQUIRED)} markers are not decoration.
 * Without them the OpenAPI spec calls every field optional, and the TypeScript
 * generated from it types all of them as possibly-undefined - so the React code
 * ends up littered with guards for fields that can never actually be missing.
 * Saying what is guaranteed here is what makes the generated types worth having.
 */
public record ApplicationResponse(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        CompanySummary company,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String roleTitle,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ApplicationStatus status,

        /** So the UI can render only the buttons that are actually legal. */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Set<ApplicationStatus> allowedNextStatuses,

        String source,
        String jobUrl,
        String location,
        RemoteType remoteType,
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        SalaryPeriod salaryPeriod,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Integer priority,

        String resumeVersion,
        String notes,
        Instant appliedAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean archived,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt
) {
}
