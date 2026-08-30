package com.jobtracker.application.dto;

import com.jobtracker.application.ApplicationStatus;
import com.jobtracker.application.RemoteType;

import java.time.Instant;
import java.util.Set;

/**
 * What the API returns. Separate from the entity on purpose: renaming a column
 * should never be a breaking API change, and lazily-loaded JPA relations must
 * never reach the JSON serializer.
 */
public record ApplicationResponse(
        Long id,
        CompanySummary company,
        String roleTitle,
        ApplicationStatus status,

        /** So the UI can render only the buttons that are actually legal. */
        Set<ApplicationStatus> allowedNextStatuses,

        String source,
        String jobUrl,
        String location,
        RemoteType remoteType,
        Integer salaryMin,
        Integer salaryMax,
        String currency,
        Integer priority,
        String resumeVersion,
        String notes,
        Instant appliedAt,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
}
