package com.jobtracker.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests, no Spring context: the class reads two headers and has one
 * interesting decision in it. Binding a request by hand is also the honest way
 * to test the "no request at all" branch, which no HTTP test could reach.
 */
class HeaderActorContextTest {

    private final HeaderActorContext context = new HeaderActorContext();

    @AfterEach
    void clearRequest() {
        // Leaking a bound request into the next test would make failures depend
        // on execution order, which is the worst kind of flake to chase.
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("no header means a person - that is what the browser sends")
    void missingHeaderIsHuman() {
        bind(new MockHttpServletRequest());

        assertThat(context.current()).isEqualTo(Actor.HUMAN);
        assertThat(context.detail()).isNull();
    }

    @Test
    @DisplayName("a worker identifies itself and names the job")
    void automationHeaderIsRead() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Actor", "automation");
        request.addHeader("X-Actor-Detail", "nudge_stale");
        bind(request);

        assertThat(context.current()).isEqualTo(Actor.AUTOMATION);
        assertThat(context.detail()).isEqualTo("nudge_stale");
    }

    @Test
    @DisplayName("an unrecognised actor is rejected, not a silent fallback to HUMAN")
    void unknownActorIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Actor", "automaton");
        bind(request);

        // The whole point of the column is provenance. A typo that quietly
        // recorded a bot's write as a person's would corrupt the one field
        // nobody would think to double-check.
        //
        // BusinessRuleException rather than IllegalArgumentException: only
        // exceptions this code raises on purpose earn a 400. See that class.
        assertThatThrownBy(context::current)
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("X-Actor");
    }

    @Test
    @DisplayName("a detail longer than the column is rejected rather than truncated")
    void overlongDetailIsRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Actor-Detail", "j".repeat(65));
        bind(request);

        assertThatThrownBy(context::detail).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("outside a request there is no person to blame, so it is SYSTEM")
    void noBoundRequestIsSystem() {
        assertThat(context.current()).isEqualTo(Actor.SYSTEM);
        assertThat(context.detail()).isNull();
    }

    private static void bind(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
