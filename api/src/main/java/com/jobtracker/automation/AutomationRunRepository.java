package com.jobtracker.automation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AutomationRunRepository extends JpaRepository<AutomationRun, Long> {

    Page<AutomationRun> findByJobNameOrderByStartedAtDesc(String jobName, Pageable pageable);

    Page<AutomationRun> findAllByOrderByStartedAtDesc(Pageable pageable);

    /**
     * The most recent run of each job - one row per job name, for the dashboard's
     * "last ran" column.
     *
     * <p>Grouping on max(id) rather than max(startedAt) is deliberate: ids are
     * assigned in insert order, so this is unambiguous even when two runs of the
     * same job start within the same clock tick, where max(startedAt) would tie.
     */
    @Query("""
            select r from AutomationRun r
            where r.id in (select max(r2.id) from AutomationRun r2 group by r2.jobName)
            order by r.jobName asc
            """)
    List<AutomationRun> findLatestPerJob();
}
