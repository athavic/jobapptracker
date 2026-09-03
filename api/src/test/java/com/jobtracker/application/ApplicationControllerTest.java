package com.jobtracker.application;

import com.jobtracker.application.dto.ApplicationEventResponse;
import com.jobtracker.application.dto.ApplicationResponse;
import com.jobtracker.application.dto.PageResponse;
import com.jobtracker.application.dto.CompanySummary;
import com.jobtracker.common.Actor;
import com.jobtracker.common.BusinessRuleException;
import com.jobtracker.common.FieldLimits;
import com.jobtracker.common.InvalidStatusTransitionException;
import com.jobtracker.common.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A web-layer slice test: real HTTP mapping, real validation, real exception
 * handling - with the service mocked out. No database, so it runs anywhere.
 *
 * <p>Security filters are off. Not because security does not matter, but because
 * a slice does not load the real SecurityConfig - it gets Spring Boot's default
 * chain instead, so every assertion here would be about a configuration this
 * application never runs. Leaving them on would mean 27 tests failing on CSRF
 * and redirects while proving nothing. What the API actually allows and refuses
 * is asserted in ApiSecurityTest, against the real chain.
 */
@WebMvcTest(ApplicationController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationService service;

    @Test
    @DisplayName("POST returns 201 and a Location header")
    void createReturnsCreated() throws Exception {
        given(service.create(any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName": "Stripe", "roleTitle": "Backend Engineer"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/applications/1"))
                .andExpect(jsonPath("$.company.name").value("Stripe"))
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    @DisplayName("POST with a blank role title returns 400 and names the bad field")
    void createRejectsInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName": "Stripe", "roleTitle": "  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors.roleTitle").exists());
    }

    @Test
    @DisplayName("an illegal status transition returns 409, not 500")
    void illegalTransitionReturnsConflict() throws Exception {
        willThrow(new InvalidStatusTransitionException("Cannot move from SAVED to ACCEPTED"))
                .given(service).changeStatus(eq(1L), any());

        mockMvc.perform(post("/api/v1/applications/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "ACCEPTED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Illegal status transition"));
    }

    @Test
    @DisplayName("an unknown id returns 404 as a problem detail")
    void unknownIdReturnsNotFound() throws Exception {
        given(service.get(999L)).willThrow(new NotFoundException("Application", 999L));

        mockMvc.perform(get("/api/v1/applications/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Application 999 not found"));
    }

    @Test
    @DisplayName("rejects a job URL with no scheme, which the browser would treat as relative")
    void createRejectsSchemelessUrl() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName": "ATLEES", "roleTitle": "Intro SWE", "jobUrl": "ATLEES.COM"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.jobUrl").exists());
    }

    @Test
    @DisplayName("accepts an absolute job URL")
    void createAcceptsAbsoluteUrl() throws Exception {
        given(service.create(any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName": "ATLEES", "roleTitle": "Intro SWE", "jobUrl": "https://db.recsolu.com/external/requisitions/abc"}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a blank job URL is allowed - the service turns it into null")
    void createAllowsBlankUrl() throws Exception {
        given(service.create(any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName": "ATLEES", "roleTitle": "Intro SWE", "jobUrl": ""}
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("accepts a fixed decimal hourly salary")
    void createAcceptsFixedHourlySalary() throws Exception {
        given(service.create(any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyName": "Stripe",
                                  "roleTitle": "Support Engineer",
                                  "salaryMin": 27.50,
                                  "salaryMax": 27.50,
                                  "currency": "USD",
                                  "salaryPeriod": "HOURLY"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("rejects a negative salary")
    void createRejectsNegativeSalary() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName": "Stripe", "roleTitle": "Engineer", "salaryMin": -1}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.salaryMin").exists());
    }

    @Test
    @DisplayName("losing an optimistic-lock race is a 409, not a 500")
    void concurrentModificationReturnsConflict() throws Exception {
        // What makes the version column useful is this mapping. Unhandled, a
        // lock failure is a server error: the Python worker reads that as "the
        // API is broken" and fails its whole run, when the truth is the far
        // less alarming "a human edited this row while you were scanning".
        // 409 is a status nudge_stale already handles by skipping the row.
        willThrow(new OptimisticLockingFailureException("Row was updated by another transaction"))
                .given(service).changeStatus(eq(1L), any());

        mockMvc.perform(post("/api/v1/applications/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "GHOSTED"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent modification"));
    }

    @Test
    @DisplayName("notes has an upper bound, because its column does not")
    void createRejectsOversizedNotes() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format("""
                                {"companyName": "Stripe", "roleTitle": "Engineer", "notes": "%s"}
                                """, "n".repeat(FieldLimits.NOTES + 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.notes").exists());
    }

    @Test
    @DisplayName("PATCH leaves out whatever it does not mention")
    void patchWithoutCompanyOrRoleIsFine() throws Exception {
        given(service.update(eq(1L), any())).willReturn(sampleResponse());

        // The case the blank check must not break: absent still means
        // "leave alone", which is the entire contract of this endpoint.
        mockMvc.perform(patch("/api/v1/applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"location": "Berlin"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH refuses to blank out the role title")
    void patchRejectsBlankRoleTitle() throws Exception {
        mockMvc.perform(patch("/api/v1/applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"roleTitle": "   "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.roleTitle").exists());
    }

    @Test
    @DisplayName("PATCH refuses to blank out the company, which would create a nameless one")
    void patchRejectsBlankCompanyName() throws Exception {
        mockMvc.perform(patch("/api/v1/applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.companyName").exists());
    }

    @Test
    @DisplayName("a business rule the service enforces is a 400 that explains itself")
    void businessRuleViolationReturnsBadRequest() throws Exception {
        willThrow(new BusinessRuleException("salaryMax (1) must be greater than or equal to salaryMin (2)"))
                .given(service).create(any());

        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName": "Stripe", "roleTitle": "Engineer"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "salaryMax (1) must be greater than or equal to salaryMin (2)"));
    }

    @Test
    @DisplayName("an unexpected IllegalArgumentException is not blamed on the caller")
    void unexpectedIllegalArgumentIsNotBlamedOnTheCaller() throws Exception {
        // The regression this guards: the handler used to catch every
        // IllegalArgumentException, so a NumberFormatException from deep inside
        // Jackson or a Spring internal came back as "your request was invalid",
        // with the internal message attached. The caller then goes looking for a
        // fault in a request that was fine, and the real bug never surfaces.
        willThrow(new IllegalArgumentException("Comparison method violates its general contract!"))
                .given(service).create(any());

        // Asserted as "escapes the handler chain" rather than as a 500, because
        // that is what a slice test can honestly see: MockMvc rethrows an
        // exception nothing handled instead of synthesising the container's
        // error page. Reaching the container at all is the point - that is the
        // path that logs a stack trace and returns a 500.
        assertThatThrownBy(() ->
                mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName": "Stripe", "roleTitle": "Engineer"}
                                """)))
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the timeline serialises both statuses and the job that made the change")
    void eventsAreReturnedAsATimeline() throws Exception {
        given(service.events(1L)).willReturn(List.of(new ApplicationEventResponse(
                9L,
                ApplicationEventType.STATUS_CHANGED,
                ApplicationStatus.APPLIED,
                ApplicationStatus.GHOSTED,
                Actor.AUTOMATION,
                "nudge_stale",
                "no reply in 30 days",
                Instant.parse("2026-08-30T10:00:00Z"))));

        mockMvc.perform(get("/api/v1/applications/1/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("STATUS_CHANGED"))
                .andExpect(jsonPath("$[0].fromStatus").value("APPLIED"))
                .andExpect(jsonPath("$[0].toStatus").value("GHOSTED"))
                .andExpect(jsonPath("$[0].actor").value("AUTOMATION"))
                .andExpect(jsonPath("$[0].actorDetail").value("nudge_stale"));
    }

    @Test
    @DisplayName("the timeline of an unknown application is a 404, not an empty list")
    void eventsForUnknownIdReturnNotFound() throws Exception {
        given(service.events(999L)).willThrow(new NotFoundException("Application", 999L));

        mockMvc.perform(get("/api/v1/applications/999/events"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("the status filter accepts several values and passes all of them down")
    void listAcceptsSeveralStatuses() throws Exception {
        given(service.search(any(), any(), anyBoolean(), any())).willReturn(emptyPage());

        mockMvc.perform(get("/api/v1/applications")
                        .param("status", "APPLIED")
                        .param("status", "SCREEN"))
                .andExpect(status().isOk());

        assertThat(capturedStatuses())
                .containsExactly(ApplicationStatus.APPLIED, ApplicationStatus.SCREEN);
    }

    @Test
    @DisplayName("one status still works - the single-value callers did not have to change")
    void listStillAcceptsASingleStatus() throws Exception {
        given(service.search(any(), any(), anyBoolean(), any())).willReturn(emptyPage());

        mockMvc.perform(get("/api/v1/applications").param("status", "APPLIED"))
                .andExpect(status().isOk());

        assertThat(capturedStatuses()).containsExactly(ApplicationStatus.APPLIED);
    }

    @Test
    @DisplayName("no status at all arrives as null, not as an empty list")
    void listWithNoStatusFiltersNothing() throws Exception {
        given(service.search(any(), any(), anyBoolean(), any())).willReturn(emptyPage());

        mockMvc.perform(get("/api/v1/applications")).andExpect(status().isOk());

        assertThat(capturedStatuses()).isNull();
    }

    /**
     * The status assertion was always here; the body assertions are the point.
     *
     * <p>Spring rejects this before the controller method runs, so
     * GlobalExceptionHandler never sees it - which used to mean the one error
     * shape the frontend knows how to read did not apply to it. It answered
     * with Boot's default body, whose only readable field is a generic "Bad
     * Request", and ErrorNotice had nothing to print but the status code.
     * spring.mvc.problemdetails.enabled is what closes that gap.
     */
    @Test
    @DisplayName("an unknown status is a 400, and a problem detail like every other error")
    void listRejectsAnUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/v1/applications").param("status", "PENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").exists());
    }

    /**
     * The same gap, reached a different way: a body Jackson cannot parse is
     * rejected before any controller method too. Worth its own test because it
     * is the failure a hand-written curl hits first.
     */
    @Test
    @DisplayName("a malformed body is a problem detail, not Boot's default error page")
    void malformedJsonIsAProblemDetail() throws Exception {
        mockMvc.perform(post("/api/v1/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").exists());
    }

    @SuppressWarnings("unchecked")
    private List<ApplicationStatus> capturedStatuses() {
        ArgumentCaptor<List<ApplicationStatus>> captor = ArgumentCaptor.forClass(List.class);
        verify(service).search(captor.capture(), any(), anyBoolean(), any());
        return captor.getValue();
    }

    private static PageResponse<ApplicationResponse> emptyPage() {
        return new PageResponse<>(List.of(), 0, 20, 0, 0, true, true);
    }

    @Test
    @DisplayName("archiving returns 204 and no body")
    void archiveReturnsNoContent() throws Exception {
        mockMvc.perform(post("/api/v1/applications/1/archive"))
                .andExpect(status().isNoContent());

        then(service).should().archive(1L);
    }

    @Test
    @DisplayName("deleting returns 204")
    void deleteReturnsNoContent() throws Exception {
        mockMvc.perform(delete("/api/v1/applications/1"))
                .andExpect(status().isNoContent());

        then(service).should().delete(1L);
    }

    /**
     * The UI deletes from a confirmation dialog that was opened against a row it
     * had already loaded, so the interesting case is the row disappearing in
     * between - another tab, or an automation job. That has to arrive as a 404
     * the dialog can show, not a 500.
     */
    @Test
    @DisplayName("deleting an id that is already gone returns 404, not 500")
    void deleteUnknownIdReturnsNotFound() throws Exception {
        willThrow(new NotFoundException("Application", 999L)).given(service).delete(999L);

        mockMvc.perform(delete("/api/v1/applications/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Application 999 not found"));
    }

    private static ApplicationResponse sampleResponse() {
        return new ApplicationResponse(
                1L,
                new CompanySummary(7L, "Stripe", null),
                "Backend Engineer",
                ApplicationStatus.APPLIED,
                Set.of(ApplicationStatus.SCREEN, ApplicationStatus.REJECTED),
                "careers page", null, "Remote", RemoteType.REMOTE,
                null, null, null, null, 3, null, null,
                Instant.parse("2026-08-30T10:00:00Z"),
                false,
                Instant.parse("2026-08-30T10:00:00Z"),
                Instant.parse("2026-08-30T10:00:00Z"));
    }
}
