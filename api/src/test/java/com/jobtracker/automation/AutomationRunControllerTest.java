package com.jobtracker.automation;

import com.jobtracker.automation.dto.AutomationRunResponse;
import com.jobtracker.common.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP contract the Python worker depends on. A slice test with the service
 * mocked, for the same reason ApplicationControllerTest is one: no database, so
 * it runs in milliseconds and only fails when the web layer is actually wrong.
 */
@WebMvcTest(AutomationRunController.class)
// Filters off for the same reason as ApplicationControllerTest: a slice runs
// Spring Boot's default security chain, not ours. See ApiSecurityTest.
@AutoConfigureMockMvc(addFilters = false)
class AutomationRunControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AutomationRunService service;

    private static AutomationRunResponse running(long id) {
        return new AutomationRunResponse(id, "nudge_stale", AutomationRunStatus.RUNNING,
                TriggerSource.MANUAL, Instant.parse("2026-08-30T10:00:00Z"),
                null, 0, 0, null, null);
    }

    @Test
    @DisplayName("starting a run returns 201 and a Location header")
    void startReturnsCreated() throws Exception {
        given(service.start(any())).willReturn(running(7L));

        mockMvc.perform(post("/api/v1/automation/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobName\":\"nudge_stale\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        "http://localhost/api/v1/automation/runs/7"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.finishedAt").doesNotExist());
    }

    @Test
    @DisplayName("a job name that is not lower_snake_case is rejected before the service sees it")
    void rejectsMalformedJobName() throws Exception {
        mockMvc.perform(post("/api/v1/automation/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobName\":\"Nudge Stale\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.jobName").exists());
    }

    @Test
    @DisplayName("completing a run echoes the outcome and the jsonb details")
    void completeReturnsOutcome() throws Exception {
        given(service.complete(eq(7L), any())).willReturn(new AutomationRunResponse(
                7L, "nudge_stale", AutomationRunStatus.SUCCEEDED, TriggerSource.MANUAL,
                Instant.parse("2026-08-30T10:00:00Z"), Instant.parse("2026-08-30T10:00:02Z"),
                12, 3, "3 stale of 12 scanned", Map.of("staleIds", java.util.List.of(1, 2, 3))));

        mockMvc.perform(post("/api/v1/automation/runs/7/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCEEDED\",\"itemsScanned\":12,\"itemsAffected\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemsAffected").value(3))
                .andExpect(jsonPath("$.details.staleIds[0]").value(1));
    }

    @Test
    @DisplayName("completing the same run twice is a 409, not a silent overwrite")
    void doubleCompleteConflicts() throws Exception {
        willThrow(new RunAlreadyFinishedException("Automation run 7 already finished as SUCCEEDED"))
                .given(service).complete(eq(7L), any());

        mockMvc.perform(post("/api/v1/automation/runs/7/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Run already finished"));
    }

    @Test
    @DisplayName("completing a run that does not exist is a 404")
    void completeUnknownRun() throws Exception {
        willThrow(new NotFoundException("Automation run", 99L))
                .given(service).complete(eq(99L), any());

        mockMvc.perform(post("/api/v1/automation/runs/99/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUCCEEDED\"}"))
                .andExpect(status().isNotFound());
    }
}
