package com.jobtracker.application.dto;

import com.jobtracker.application.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(

        @NotNull
        ApplicationStatus status,

        /** Free-text reason. In phase 4 this becomes an application_event row. */
        String note
) {
}
