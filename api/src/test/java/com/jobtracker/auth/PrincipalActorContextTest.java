package com.jobtracker.auth;

import com.jobtracker.common.Actor;
import com.jobtracker.common.BusinessRuleException;
import com.jobtracker.tenancy.SignedInUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain unit tests, no Spring context. The class turns "how did this request
 * authenticate" into an Actor, and setting the security context by hand is also
 * the only way to reach the branch where there is no principal at all.
 *
 * <p>Replaces HeaderActorContextTest. The behaviour that test asserted - that a
 * caller could name itself AUTOMATION - is now the behaviour this one asserts
 * is impossible.
 */
class PrincipalActorContextTest {

    private final PrincipalActorContext context = new PrincipalActorContext();

    @AfterEach
    void clearContext() {
        // Leaking either of these into the next test would make failures depend
        // on execution order, the worst kind of flake to chase.
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("a signed-in person is HUMAN, and cannot claim to be a job")
    void signedInUserIsHuman() {
        signedInAsUser();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(PrincipalActorContext.DETAIL_HEADER, "nudge_stale");
        bind(request);

        assertThat(context.current()).isEqualTo(Actor.HUMAN);

        // The header is present and deliberately ignored. Under the old
        // header-trusting implementation this same request would have filed a
        // person's change under a machine's name.
        assertThat(context.detail()).isNull();
    }

    @Test
    @DisplayName("the service key is AUTOMATION, and may name the job it is running")
    void serviceKeyIsAutomation() {
        authenticatedAsService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(PrincipalActorContext.DETAIL_HEADER, "nudge_stale");
        bind(request);

        assertThat(context.current()).isEqualTo(Actor.AUTOMATION);
        assertThat(context.detail()).isEqualTo("nudge_stale");
    }

    @Test
    @DisplayName("a worker that names no job is still a worker")
    void automationWithoutDetail() {
        authenticatedAsService();
        bind(new MockHttpServletRequest());

        assertThat(context.current()).isEqualTo(Actor.AUTOMATION);
        assertThat(context.detail()).isNull();
    }

    @Test
    @DisplayName("a detail longer than the column is rejected rather than truncated")
    void overlongDetailIsRejected() {
        authenticatedAsService();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(PrincipalActorContext.DETAIL_HEADER, "j".repeat(65));
        bind(request);

        assertThatThrownBy(context::detail).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("with no principal at all there is nobody to blame, so it is SYSTEM")
    void noAuthenticationIsSystem() {
        assertThat(context.current()).isEqualTo(Actor.SYSTEM);
        assertThat(context.detail()).isNull();
    }

    private static void signedInAsUser() {
        OidcUser google = Mockito.mock(OidcUser.class);
        WorkspaceUser principal = new WorkspaceUser(
                google, new SignedInUser(1L, 7L, "a@example.com", "A", null));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private static void authenticatedAsService() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "automation", null,
                        List.of(new SimpleGrantedAuthority(
                                ServiceKeyAuthenticationFilter.ROLE_SERVICE))));
    }

    private static void bind(MockHttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
