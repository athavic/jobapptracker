package com.jobtracker.application;

import com.jobtracker.application.dto.ApplicationEventResponse;

/**
 * Entity to DTO. Note what is absent: the application itself. It is a LAZY
 * relation, and touching it here would issue one select per event - the exact
 * N+1 that ApplicationSpecs.fetchCompany exists to avoid on the list endpoint.
 * The caller already knows which application it asked for.
 */
final class ApplicationEventMapper {

    private ApplicationEventMapper() {
    }

    static ApplicationEventResponse toResponse(ApplicationEvent e) {
        return new ApplicationEventResponse(
                e.getId(),
                e.getType(),
                e.getFromStatus(),
                e.getToStatus(),
                e.getActor(),
                e.getActorDetail(),
                e.getNote(),
                e.getOccurredAt());
    }
}
