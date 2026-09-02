package com.jobtracker.application.dto;

import com.jobtracker.application.ApplicationStatus;
import com.jobtracker.common.FieldLimits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeStatusRequest(

        @NotNull
        ApplicationStatus status,

        /** Free-text reason. Stored on the application_event row this change writes. */
        @Size(max = FieldLimits.SHORT_NOTE) String note
) {
}
