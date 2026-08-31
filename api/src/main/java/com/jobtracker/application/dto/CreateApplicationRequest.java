package com.jobtracker.application.dto;

import com.jobtracker.application.ApplicationStatus;
import com.jobtracker.application.RemoteType;
import com.jobtracker.application.SalaryPeriod;
import com.jobtracker.common.ValidationPatterns;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.math.BigDecimal;

/**
 * What a client sends to create an application.
 *
 * <p>Note this is NOT the entity: it takes a company <em>name</em>, not an id, so
 * the caller never has to create a company first. The service looks it up or
 * creates it.
 */
public record CreateApplicationRequest(

        @NotBlank @Size(max = 200)
        String companyName,

        @NotBlank @Size(max = 250)
        String roleTitle,

        /** Optional. Defaults to SAVED. */
        ApplicationStatus status,

        @Size(max = 64) String source,
        @Pattern(regexp = ValidationPatterns.HTTP_URL,
                message = "must be an absolute URL starting with http:// or https://")
        @Size(max = 1000) String jobUrl,
        @Size(max = 200) String location,

        RemoteType remoteType,

        @DecimalMin("0.00") BigDecimal salaryMin,
        @DecimalMin("0.00") BigDecimal salaryMax,

        @Size(min = 3, max = 3) String currency,

        SalaryPeriod salaryPeriod,

        @Min(1) @Max(5) Integer priority,

        @Size(max = 100) String resumeVersion,

        String notes,

        Instant appliedAt
) {
}
