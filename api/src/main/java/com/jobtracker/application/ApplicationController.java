package com.jobtracker.application;

import com.jobtracker.application.dto.ApplicationEventResponse;
import com.jobtracker.application.dto.ApplicationResponse;
import com.jobtracker.application.dto.ChangeStatusRequest;
import com.jobtracker.application.dto.CreateApplicationRequest;
import com.jobtracker.application.dto.PageResponse;
import com.jobtracker.application.dto.UpdateApplicationRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * The HTTP edge. Deliberately thin: routing, status codes, and validation only.
 * Anything that decides something belongs in {@link ApplicationService}.
 */
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ApplicationResponse> list(
            // Repeatable: ?status=APPLIED&status=SCREEN. Spring binds either form,
            // so a caller sending a single value - including every existing one -
            // keeps working unchanged.
            @RequestParam(required = false) List<ApplicationStatus> status,
            @RequestParam(required = false) String company,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            // @ParameterObject flattens Pageable into page/size/sort in the OpenAPI
            // spec. Without it springdoc documents one opaque "pageable" object,
            // which is not what Spring actually accepts on the wire.
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return service.search(status, company, includeArchived, pageable);
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    /** 201 plus a Location header pointing at the new resource - the REST convention. */
    @PostMapping
    public ResponseEntity<ApplicationResponse> create(
            @Valid @RequestBody CreateApplicationRequest request,
            UriComponentsBuilder uriBuilder) {

        ApplicationResponse created = service.create(request);

        URI location = uriBuilder
                .path("/api/v1/applications/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @PatchMapping("/{id}")
    public ApplicationResponse update(@PathVariable Long id,
                                      @Valid @RequestBody UpdateApplicationRequest request) {
        return service.update(id, request);
    }

    /**
     * Status lives behind its own endpoint rather than in PATCH, so a transition
     * can never be smuggled in as an ordinary field update.
     */
    @PostMapping("/{id}/status")
    public ApplicationResponse changeStatus(@PathVariable Long id,
                                            @Valid @RequestBody ChangeStatusRequest request) {
        return service.changeStatus(id, request);
    }

    /**
     * The timeline. A plain list rather than a page: an application accumulates
     * events at human speed, so paging would be ceremony around a dozen rows.
     */
    @GetMapping("/{id}/events")
    public List<ApplicationEventResponse> events(@PathVariable Long id) {
        return service.events(id);
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        service.archive(id);
        return ResponseEntity.noContent().build();
    }

    /** Really deletes. Prefer /archive unless you typed it in by mistake. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
