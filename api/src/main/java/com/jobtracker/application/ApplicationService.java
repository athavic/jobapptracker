package com.jobtracker.application;

import com.jobtracker.application.dto.ApplicationResponse;
import com.jobtracker.application.dto.ChangeStatusRequest;
import com.jobtracker.application.dto.CreateApplicationRequest;
import com.jobtracker.application.dto.PageResponse;
import com.jobtracker.application.dto.UpdateApplicationRequest;
import com.jobtracker.common.InvalidStatusTransitionException;
import com.jobtracker.common.NotFoundException;
import com.jobtracker.company.Company;
import com.jobtracker.company.CompanyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Everything that is true about an application regardless of who is asking -
 * the React form, curl, or (from phase 3) a Python job.
 */
@Service
public class ApplicationService {

    private final JobApplicationRepository applications;
    private final CompanyRepository companies;

    public ApplicationService(JobApplicationRepository applications, CompanyRepository companies) {
        this.applications = applications;
        this.companies = companies;
    }

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest request) {
        Company company = findOrCreateCompany(request.companyName());

        ApplicationStatus status =
                request.status() != null ? request.status() : ApplicationStatus.SAVED;

        JobApplication application = new JobApplication(company, request.roleTitle().trim(), status);
        application.setSource(blankToNull(request.source()));
        application.setJobUrl(blankToNull(request.jobUrl()));
        application.setLocation(blankToNull(request.location()));
        application.setRemoteType(request.remoteType());
        application.setSalaryMin(request.salaryMin());
        application.setSalaryMax(request.salaryMax());
        application.setCurrency(normalizeCurrency(request.currency()));
        application.setResumeVersion(blankToNull(request.resumeVersion()));
        application.setNotes(blankToNull(request.notes()));

        if (request.priority() != null) {
            application.setPriority(request.priority());
        }

        // Convenience: if you record it as already applied and did not say when,
        // assume now. Beats making every caller send a timestamp.
        if (request.appliedAt() != null) {
            application.setAppliedAt(request.appliedAt());
        } else if (status != ApplicationStatus.DISCOVERED && status != ApplicationStatus.SAVED) {
            application.setAppliedAt(Instant.now());
        }

        validateSalaryRange(application);

        return ApplicationMapper.toResponse(applications.save(application));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(Long id) {
        return ApplicationMapper.toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> search(ApplicationStatus status,
                                                    String companyName,
                                                    boolean includeArchived,
                                                    Pageable pageable) {
        Specification<JobApplication> spec = ApplicationSpecs.fetchCompany()
                .and(ApplicationSpecs.hasStatus(status))
                .and(ApplicationSpecs.companyNameContains(companyName))
                .and(ApplicationSpecs.archivedFilter(includeArchived));

        Page<JobApplication> page = applications.findAll(spec, pageable);
        return PageResponse.from(page, ApplicationMapper::toResponse);
    }

    @Transactional
    public ApplicationResponse update(Long id, UpdateApplicationRequest request) {
        JobApplication application = load(id);

        // null means "leave alone" - see the note on UpdateApplicationRequest.
        if (request.companyName() != null) {
            application.setCompany(findOrCreateCompany(request.companyName()));
        }
        if (request.roleTitle() != null) {
            application.setRoleTitle(request.roleTitle().trim());
        }
        if (request.source() != null) {
            application.setSource(blankToNull(request.source()));
        }
        if (request.jobUrl() != null) {
            application.setJobUrl(blankToNull(request.jobUrl()));
        }
        if (request.location() != null) {
            application.setLocation(blankToNull(request.location()));
        }
        if (request.remoteType() != null) {
            application.setRemoteType(request.remoteType());
        }
        if (request.salaryMin() != null) {
            application.setSalaryMin(request.salaryMin());
        }
        if (request.salaryMax() != null) {
            application.setSalaryMax(request.salaryMax());
        }
        if (request.currency() != null) {
            application.setCurrency(normalizeCurrency(request.currency()));
        }
        if (request.priority() != null) {
            application.setPriority(request.priority());
        }
        if (request.resumeVersion() != null) {
            application.setResumeVersion(blankToNull(request.resumeVersion()));
        }
        if (request.notes() != null) {
            application.setNotes(blankToNull(request.notes()));
        }
        if (request.appliedAt() != null) {
            application.setAppliedAt(request.appliedAt());
        }
        if (request.archived() != null) {
            application.setArchived(request.archived());
        }

        validateSalaryRange(application);

        // No save() call needed: the entity is managed, so JPA flushes the changes
        // when the transaction commits. This surprises everyone exactly once.
        return ApplicationMapper.toResponse(application);
    }

    /**
     * The one method that enforces the lifecycle. In phase 4 this also writes an
     * application_event row, which is what makes the timeline - and the question
     * "did I change this, or did a bot?" - answerable.
     */
    @Transactional
    public ApplicationResponse changeStatus(Long id, ChangeStatusRequest request) {
        JobApplication application = load(id);
        ApplicationStatus current = application.getStatus();
        ApplicationStatus next = request.status();

        if (!current.canTransitionTo(next)) {
            throw new InvalidStatusTransitionException(
                    "Cannot move from " + current + " to " + next
                            + ". Allowed from " + current + ": " + current.allowedNext());
        }

        application.setStatus(next);

        if (next == ApplicationStatus.APPLIED && application.getAppliedAt() == null) {
            application.setAppliedAt(Instant.now());
        }

        return ApplicationMapper.toResponse(application);
    }

    /** Archive rather than delete - you want the history for the stats later. */
    @Transactional
    public void archive(Long id) {
        load(id).setArchived(true);
    }

    @Transactional
    public void delete(Long id) {
        if (!applications.existsById(id)) {
            throw new NotFoundException("Application", id);
        }
        applications.deleteById(id);
    }

    private JobApplication load(Long id) {
        return applications.findWithCompanyById(id)
                .orElseThrow(() -> new NotFoundException("Application", id));
    }

    private Company findOrCreateCompany(String rawName) {
        String name = rawName.trim();
        return companies.findByNameIgnoreCase(name)
                .orElseGet(() -> companies.save(new Company(name)));
    }

    private static String normalizeCurrency(String currency) {
        return currency == null ? null : currency.trim().toUpperCase();
    }

    /**
     * Empty string and null both mean "no value", and storing both means every
     * reader has to check for both. Collapse them here, at the one place data
     * enters the system.
     */
    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * The database has the same CHECK constraint. Doing it here too turns a raw
     * constraint violation into a readable 400 for the caller.
     */
    private static void validateSalaryRange(JobApplication application) {
        Integer min = application.getSalaryMin();
        Integer max = application.getSalaryMax();
        if (min != null && max != null && max < min) {
            throw new IllegalArgumentException(
                    "salaryMax (" + max + ") must be greater than or equal to salaryMin (" + min + ")");
        }
    }
}
