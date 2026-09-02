package com.jobtracker.application;

import com.jobtracker.application.dto.ApplicationEventResponse;
import com.jobtracker.application.dto.ApplicationResponse;
import com.jobtracker.application.dto.CompanySummary;
import com.jobtracker.common.Actor;
import com.jobtracker.common.BusinessRuleException;
import com.jobtracker.common.InvalidStatusTransitionException;
import com.jobtracker.common.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A web-layer slice test: real HTTP mapping, real validation, real exception
 * handling - with the service mocked out. No database, so it runs anywhere.
 */
@WebMvcTest(ApplicationController.class)
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
    @DisplayName("answers the browser preflight so the Vite dev server can call the API")
    void corsPreflightIsAllowed() throws Exception {
        mockMvc.perform(options("/api/v1/applications")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
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
