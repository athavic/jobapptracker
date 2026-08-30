package com.jobtracker.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record CompanySummary(

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        String website
) {
}
