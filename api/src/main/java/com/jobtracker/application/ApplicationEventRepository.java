package com.jobtracker.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, Long> {

    /**
     * Newest first, with id as the tie-break.
     *
     * <p>The tie-break is not decoration: creating an application writes its
     * CREATED event and, if it was recorded as already applied, its first
     * STATUS_CHANGED within the same transaction and often the same millisecond.
     * Ordering on the timestamp alone leaves those two in whatever order the
     * planner feels like, and a timeline that sometimes reads backwards is a bug
     * that only shows up on someone else's machine.
     */
    List<ApplicationEvent> findByApplicationIdOrderByOccurredAtDescIdDesc(Long applicationId);
}
