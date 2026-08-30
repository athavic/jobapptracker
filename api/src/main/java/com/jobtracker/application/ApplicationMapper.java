package com.jobtracker.application;

import com.jobtracker.application.dto.ApplicationResponse;
import com.jobtracker.application.dto.CompanySummary;
import com.jobtracker.company.Company;

/**
 * Entity to DTO, in one place.
 *
 * <p>This must run inside the transaction (open-in-view is off), which is why
 * the service maps before returning rather than handing entities to the controller.
 */
final class ApplicationMapper {

    private ApplicationMapper() {
    }

    static ApplicationResponse toResponse(JobApplication a) {
        Company c = a.getCompany();
        return new ApplicationResponse(
                a.getId(),
                new CompanySummary(c.getId(), c.getName(), c.getWebsite()),
                a.getRoleTitle(),
                a.getStatus(),
                a.getStatus().allowedNext(),
                a.getSource(),
                a.getJobUrl(),
                a.getLocation(),
                a.getRemoteType(),
                a.getSalaryMin(),
                a.getSalaryMax(),
                a.getCurrency(),
                a.getPriority(),
                a.getResumeVersion(),
                a.getNotes(),
                a.getAppliedAt(),
                a.isArchived(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
