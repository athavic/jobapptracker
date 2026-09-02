package com.jobtracker.application;

import com.jobtracker.application.dto.ApplicationEventResponse;
import com.jobtracker.application.dto.ApplicationResponse;
import com.jobtracker.application.dto.ChangeStatusRequest;
import com.jobtracker.application.dto.CreateApplicationRequest;
import com.jobtracker.application.dto.PageResponse;
import com.jobtracker.application.dto.UpdateApplicationRequest;
import com.jobtracker.common.Actor;
import com.jobtracker.common.BusinessRuleException;
import com.jobtracker.common.ActorContext;
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
import java.math.BigDecimal;
import java.util.List;

/**
 * Everything that is true about an application regardless of who is asking -
 * the React form, curl, or (from phase 3) a Python job.
 */
@Service
public class ApplicationService {

    private final JobApplicationRepository applications;
    private final CompanyRepository companies;
    private final ApplicationEventRepository events;
    private final ActorContext actorContext;

    public ApplicationService(JobApplicationRepository applications,
                              CompanyRepository companies,
                              ApplicationEventRepository events,
                              ActorContext actorContext) {
        this.applications = applications;
        this.companies = companies;
        this.events = events;
        this.actorContext = actorContext;
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
        application.setSalaryPeriod(defaultSalaryPeriod(
                request.salaryPeriod(), request.salaryMin(), request.salaryMax()));
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

        JobApplication saved = applications.save(application);

        // Inside the same @Transactional as the insert above. If recording the
        // event fails, the application is not created either - a row with no
        // history would be indistinguishable from one whose history was lost.
        events.save(ApplicationEvent.created(saved, saved.getStatus(), actor(), actorDetail()));

        return ApplicationMapper.toResponse(saved);
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
        if (request.salaryPeriod() != null) {
            application.setSalaryPeriod(request.salaryPeriod());
        } else if (application.getSalaryPeriod() == null
                && (application.getSalaryMin() != null || application.getSalaryMax() != null)) {
            application.setSalaryPeriod(SalaryPeriod.ANNUAL);
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
            // Archiving has two entry points - this field and /archive - so the
            // event is written by one helper both call. A history with a gap
            // depending on which endpoint you happened to use is worse than no
            // history, because it looks complete.
            setArchived(application, request.archived());
        }

        validateSalaryRange(application);

        // No save() call needed: the entity is managed, so JPA flushes the changes
        // when the transaction commits. This surprises everyone exactly once.
        return ApplicationMapper.toResponse(application);
    }

    /**
     * The one method that enforces the lifecycle, and now the one that records
     * it. Both jobs live here for the same reason: this is the only code path
     * that can move a status, so it is the only place a history can be missed.
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

        // A self-transition is legal (canTransitionTo returns true for it) but it
        // is not an event: nothing changed, and a timeline padded with "APPLIED ->
        // APPLIED" from a double-clicked dropdown teaches the reader to skim.
        boolean moved = current != next;

        application.setStatus(next);

        if (next == ApplicationStatus.APPLIED && application.getAppliedAt() == null) {
            application.setAppliedAt(Instant.now());
        }

        if (moved) {
            events.save(ApplicationEvent.statusChanged(
                    application, current, next, actor(), actorDetail(),
                    blankToNull(request.note())));
        }

        return ApplicationMapper.toResponse(application);
    }

    /**
     * An application's history, newest first.
     *
     * <p>Its own endpoint rather than a field on ApplicationResponse. Embedding
     * it would make every row of the list endpoint carry a history nobody asked
     * for, and would reintroduce exactly the N+1 the fetch join removed.
     */
    @Transactional(readOnly = true)
    public List<ApplicationEventResponse> events(Long id) {
        if (!applications.existsById(id)) {
            // Checked explicitly so an unknown id is a 404 rather than a
            // cheerful empty list, which would read as "nothing ever happened".
            throw new NotFoundException("Application", id);
        }
        return events.findByApplicationIdOrderByOccurredAtDescIdDesc(id).stream()
                .map(ApplicationEventMapper::toResponse)
                .toList();
    }

    /** Archive rather than delete - you want the history for the stats later. */
    @Transactional
    public void archive(Long id) {
        setArchived(load(id), true);
    }

    /**
     * Writes the ARCHIVED event only on a genuine false -> true move, so
     * archiving something twice does not append a second identical line.
     *
     * <p>Un-archiving records nothing, because ApplicationEventType has no value
     * for it. That is a deliberate gap rather than an oversight: adding
     * UNARCHIVED is a migration plus an enum value, and it is worth doing the
     * first time un-archiving is something you actually want to explain.
     */
    private void setArchived(JobApplication application, boolean archived) {
        boolean wasArchived = application.isArchived();
        application.setArchived(archived);

        if (archived && !wasArchived) {
            events.save(ApplicationEvent.archived(application, actor(), actorDetail()));
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!applications.existsById(id)) {
            throw new NotFoundException("Application", id);
        }
        applications.deleteById(id);
    }

    /**
     * Read once per event rather than injected as a value, because ActorContext
     * answers a per-request question and this service is a singleton.
     */
    private Actor actor() {
        return actorContext.current();
    }

    private String actorDetail() {
        return actorContext.detail();
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

    private static SalaryPeriod defaultSalaryPeriod(SalaryPeriod requested,
                                                     BigDecimal minimum,
                                                     BigDecimal maximum) {
        if (requested != null) {
            return requested;
        }
        return minimum != null || maximum != null ? SalaryPeriod.ANNUAL : null;
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
        BigDecimal min = application.getSalaryMin();
        BigDecimal max = application.getSalaryMax();
        if (min != null && max != null && max.compareTo(min) < 0) {
            throw new BusinessRuleException(
                    "salaryMax (" + max + ") must be greater than or equal to salaryMin (" + min + ")");
        }
    }
}
