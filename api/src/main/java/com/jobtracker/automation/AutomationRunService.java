package com.jobtracker.automation;

import com.jobtracker.application.dto.PageResponse;
import com.jobtracker.automation.dto.AutomationRunResponse;
import com.jobtracker.automation.dto.CompleteRunRequest;
import com.jobtracker.automation.dto.StartRunRequest;
import com.jobtracker.common.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Bookkeeping for job executions.
 *
 * <p>A run is recorded in two calls - start, then complete - rather than one call
 * at the end. The extra round trip buys the only failure mode that matters: a job
 * that is killed, times out, or dies where its own error handling cannot reach
 * still leaves a RUNNING row with a start time. Reporting once at the end would
 * record nothing at all, and "nothing" is indistinguishable from "never ran".
 */
@Service
public class AutomationRunService {

    private final AutomationRunRepository runs;

    public AutomationRunService(AutomationRunRepository runs) {
        this.runs = runs;
    }

    @Transactional
    public AutomationRunResponse start(StartRunRequest request) {
        TriggerSource trigger =
                request.triggerSource() != null ? request.triggerSource() : TriggerSource.MANUAL;
        Instant startedAt =
                request.startedAt() != null ? request.startedAt() : Instant.now();

        AutomationRun run = new AutomationRun(request.jobName().trim(), trigger, startedAt);
        return AutomationRunMapper.toResponse(runs.save(run));
    }

    /**
     * Finishing is a one-way door: a run that already has an outcome cannot be
     * re-reported. A second complete means a bug in the worker - usually a retry
     * that should have started a fresh run - so it gets a 409 rather than quietly
     * overwriting the first outcome and hiding the earlier failure.
     */
    @Transactional
    public AutomationRunResponse complete(Long id, CompleteRunRequest request) {
        AutomationRun run = runs.findById(id)
                .orElseThrow(() -> new NotFoundException("Automation run", id));

        if (request.status() == AutomationRunStatus.RUNNING) {
            throw new IllegalArgumentException(
                    "status must be SUCCEEDED or FAILED when completing a run");
        }
        if (run.getStatus().isFinished()) {
            throw new RunAlreadyFinishedException(
                    "Automation run " + id + " already finished as " + run.getStatus());
        }

        Instant finishedAt =
                request.finishedAt() != null ? request.finishedAt() : Instant.now();
        if (finishedAt.isBefore(run.getStartedAt())) {
            throw new IllegalArgumentException("finishedAt cannot be before startedAt");
        }

        run.finish(
                request.status(),
                finishedAt,
                orZero(request.itemsScanned()),
                orZero(request.itemsAffected()),
                request.message(),
                request.details());

        // Managed entity inside a transaction, so no save() call - the same rule
        // that governs ApplicationService.update.
        return AutomationRunMapper.toResponse(run);
    }

    @Transactional(readOnly = true)
    public AutomationRunResponse get(Long id) {
        return runs.findById(id)
                .map(AutomationRunMapper::toResponse)
                .orElseThrow(() -> new NotFoundException("Automation run", id));
    }

    @Transactional(readOnly = true)
    public PageResponse<AutomationRunResponse> search(String jobName, Pageable pageable) {
        Page<AutomationRun> page = (jobName == null || jobName.isBlank())
                ? runs.findAllByOrderByStartedAtDesc(pageable)
                : runs.findByJobNameOrderByStartedAtDesc(jobName.trim(), pageable);

        return PageResponse.from(page, AutomationRunMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<AutomationRunResponse> latestPerJob() {
        return runs.findLatestPerJob().stream()
                .map(AutomationRunMapper::toResponse)
                .toList();
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
