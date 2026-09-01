package com.jobtracker.application;

import com.jobtracker.application.dto.ChangeStatusRequest;
import com.jobtracker.application.dto.CreateApplicationRequest;
import com.jobtracker.common.Actor;
import com.jobtracker.common.ActorContext;
import com.jobtracker.company.Company;
import com.jobtracker.company.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The service tested directly, with its repositories mocked.
 *
 * <p>Everything the events table is worth hangs on this class being the only
 * writer and never missing a write, so these assert on what reached the event
 * repository rather than on the response body - the response looks identical
 * whether or not history was recorded, which is exactly why the bug would be
 * easy to ship.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationEventWritingTest {

    @Mock
    private JobApplicationRepository applications;
    @Mock
    private CompanyRepository companies;
    @Mock
    private ApplicationEventRepository events;
    @Mock
    private ActorContext actorContext;

    private ApplicationService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationService(applications, companies, events, actorContext);
    }

    @Test
    @DisplayName("creating an application records a CREATED event at its initial status")
    void createRecordsCreatedEvent() {
        given(actorContext.current()).willReturn(Actor.HUMAN);
        given(companies.findByNameIgnoreCase("Stripe")).willReturn(Optional.of(new Company("Stripe")));
        given(applications.save(any())).willAnswer(call -> call.getArgument(0));

        service.create(new CreateApplicationRequest(
                "Stripe", "Backend Engineer", ApplicationStatus.SAVED,
                null, null, null, null, null, null, null, null, null, null, null, null));

        ApplicationEvent event = captureEvent();
        assertThat(event.getType()).isEqualTo(ApplicationEventType.CREATED);
        assertThat(event.getToStatus()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(event.getFromStatus()).isNull();
        assertThat(event.getActor()).isEqualTo(Actor.HUMAN);
    }

    @Test
    @DisplayName("a status change records both statuses, the note, and the automation that did it")
    void statusChangeRecordsBothStatuses() {
        given(actorContext.current()).willReturn(Actor.AUTOMATION);
        given(actorContext.detail()).willReturn("nudge_stale");
        givenApplicationExists(ApplicationStatus.APPLIED);

        service.changeStatus(1L, new ChangeStatusRequest(ApplicationStatus.GHOSTED, "no reply in 30 days"));

        ApplicationEvent event = captureEvent();
        assertThat(event.getType()).isEqualTo(ApplicationEventType.STATUS_CHANGED);
        assertThat(event.getFromStatus()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(event.getToStatus()).isEqualTo(ApplicationStatus.GHOSTED);
        assertThat(event.getNote()).isEqualTo("no reply in 30 days");
        assertThat(event.getActor()).isEqualTo(Actor.AUTOMATION);
        assertThat(event.getActorDetail()).isEqualTo("nudge_stale");
    }

    @Test
    @DisplayName("re-sending the status it already has writes no event")
    void selfTransitionRecordsNothing() {
        givenApplicationExists(ApplicationStatus.APPLIED);

        service.changeStatus(1L, new ChangeStatusRequest(ApplicationStatus.APPLIED, null));

        verify(events, never()).save(any());
    }

    @Test
    @DisplayName("an illegal transition writes no event - the history stays true to the state")
    void rejectedTransitionRecordsNothing() {
        givenApplicationExists(ApplicationStatus.SAVED);

        try {
            service.changeStatus(1L, new ChangeStatusRequest(ApplicationStatus.ACCEPTED, null));
        } catch (RuntimeException expected) {
            // asserted on elsewhere; here only the absence of an event matters
        }

        verify(events, never()).save(any());
    }

    @Test
    @DisplayName("archiving records one event, and archiving again records nothing")
    void archiveIsRecordedOnce() {
        given(actorContext.current()).willReturn(Actor.HUMAN);
        JobApplication application = givenApplicationExists(ApplicationStatus.APPLIED);

        service.archive(1L);
        assertThat(application.isArchived()).isTrue();
        assertThat(captureEvent().getType()).isEqualTo(ApplicationEventType.ARCHIVED);

        service.archive(1L);
        verify(events).save(any());   // still exactly one, from the first call
    }

    private JobApplication givenApplicationExists(ApplicationStatus status) {
        JobApplication application =
                new JobApplication(new Company("Stripe"), "Backend Engineer", status);
        given(applications.findWithCompanyById(1L)).willReturn(Optional.of(application));
        return application;
    }

    private ApplicationEvent captureEvent() {
        ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(events).save(captor.capture());
        return captor.getValue();
    }
}
