package com.jobtracker.automation;

import com.jobtracker.automation.dto.AutomationRunResponse;

final class AutomationRunMapper {

    private AutomationRunMapper() {
    }

    static AutomationRunResponse toResponse(AutomationRun run) {
        return new AutomationRunResponse(
                run.getId(),
                run.getJobName(),
                run.getStatus(),
                run.getTriggerSource(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getItemsScanned(),
                run.getItemsAffected(),
                run.getMessage(),
                run.getDetails()
        );
    }
}
