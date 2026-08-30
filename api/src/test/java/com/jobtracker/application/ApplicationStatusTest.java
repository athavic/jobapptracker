package com.jobtracker.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The payoff for putting the lifecycle rules in an enum instead of scattering
 * them through controllers: they are testable in milliseconds, with no Spring
 * context and no database.
 */
class ApplicationStatusTest {

    @Test
    @DisplayName("walks the happy path from SAVED to ACCEPTED")
    void happyPath() {
        assertThat(ApplicationStatus.SAVED.canTransitionTo(ApplicationStatus.APPLIED)).isTrue();
        assertThat(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.SCREEN)).isTrue();
        assertThat(ApplicationStatus.SCREEN.canTransitionTo(ApplicationStatus.INTERVIEW)).isTrue();
        assertThat(ApplicationStatus.INTERVIEW.canTransitionTo(ApplicationStatus.OFFER)).isTrue();
        assertThat(ApplicationStatus.OFFER.canTransitionTo(ApplicationStatus.ACCEPTED)).isTrue();
    }

    @Test
    @DisplayName("lets any active application be rejected, ghosted or withdrawn")
    void exitsFromAnyActiveState() {
        for (ApplicationStatus active :
                new ApplicationStatus[]{ApplicationStatus.APPLIED, ApplicationStatus.SCREEN,
                        ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER}) {

            assertThat(active.canTransitionTo(ApplicationStatus.REJECTED)).isTrue();
            assertThat(active.canTransitionTo(ApplicationStatus.GHOSTED)).isTrue();
            assertThat(active.canTransitionTo(ApplicationStatus.WITHDRAWN)).isTrue();
        }
    }

    @Test
    @DisplayName("refuses to move backwards")
    void noTimeTravel() {
        assertThat(ApplicationStatus.INTERVIEW.canTransitionTo(ApplicationStatus.APPLIED)).isFalse();
        assertThat(ApplicationStatus.OFFER.canTransitionTo(ApplicationStatus.SCREEN)).isFalse();
        assertThat(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.SAVED)).isFalse();
    }

    @Test
    @DisplayName("refuses to skip straight from SAVED to an offer")
    void noSkippingAhead() {
        assertThat(ApplicationStatus.SAVED.canTransitionTo(ApplicationStatus.OFFER)).isFalse();
        assertThat(ApplicationStatus.SAVED.canTransitionTo(ApplicationStatus.ACCEPTED)).isFalse();
    }

    @Test
    @DisplayName("terminal statuses are dead ends")
    void terminalStatesAreFinal() {
        assertThat(ApplicationStatus.REJECTED.isTerminal()).isTrue();
        assertThat(ApplicationStatus.REJECTED.allowedNext()).isEmpty();
        assertThat(ApplicationStatus.ACCEPTED.canTransitionTo(ApplicationStatus.INTERVIEW)).isFalse();
    }

    @Test
    @DisplayName("re-setting the same status is a no-op, not an error")
    void selfTransitionIsAllowed() {
        assertThat(ApplicationStatus.INTERVIEW.canTransitionTo(ApplicationStatus.INTERVIEW)).isTrue();
        assertThat(ApplicationStatus.REJECTED.canTransitionTo(ApplicationStatus.REJECTED)).isTrue();
    }
}
