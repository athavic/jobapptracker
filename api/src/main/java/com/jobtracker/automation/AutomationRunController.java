package com.jobtracker.automation;

import com.jobtracker.application.dto.PageResponse;
import com.jobtracker.automation.dto.AutomationRunResponse;
import com.jobtracker.automation.dto.CompleteRunRequest;
import com.jobtracker.automation.dto.StartRunRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/automation/runs")
public class AutomationRunController {

    private final AutomationRunService service;

    public AutomationRunController(AutomationRunService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<AutomationRunResponse> list(
            @RequestParam(required = false) String jobName,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {

        return service.search(jobName, pageable);
    }

    /**
     * One row per job - what the dashboard renders as "last ran". Declared before
     * /{id} matters less than it looks (Spring prefers the literal path over the
     * variable), but keeping them in this order keeps the intent obvious.
     */
    @GetMapping("/latest")
    public List<AutomationRunResponse> latest() {
        return service.latestPerJob();
    }

    @GetMapping("/{id}")
    public AutomationRunResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<AutomationRunResponse> start(
            @Valid @RequestBody StartRunRequest request,
            UriComponentsBuilder uriBuilder) {

        AutomationRunResponse started = service.start(request);

        URI location = uriBuilder
                .path("/api/v1/automation/runs/{id}")
                .buildAndExpand(started.id())
                .toUri();

        return ResponseEntity.created(location).body(started);
    }

    @PostMapping("/{id}/complete")
    public AutomationRunResponse complete(@PathVariable Long id,
                                          @Valid @RequestBody CompleteRunRequest request) {
        return service.complete(id, request);
    }
}
