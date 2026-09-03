package com.jobtracker.auth;

import com.jobtracker.application.ApplicationController;
import com.jobtracker.application.ApplicationService;
import com.jobtracker.common.WebConfig;
import com.jobtracker.tenancy.SignedInUser;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What the API allows and refuses, against the real SecurityConfig.
 *
 * <p>The controller slices run with filters disabled, because a slice loads
 * Spring Boot's default chain rather than ours - so this is the only place the
 * production rules are actually exercised. Every assertion here would have
 * passed before 5c too, which is the point: they are the difference between
 * "a security dependency was added" and "the API is closed".
 *
 * <p>Still no database. The two collaborators that would need one - the OIDC
 * user service and the application service - are mocked, because none of these
 * tests care what a request returns, only whether it was allowed to happen.
 */
@WebMvcTest(controllers = {ApplicationController.class, MeController.class})
@Import({SecurityConfig.class, WebConfig.class, ApiSecurityTest.ClientRegistrationStub.class})
@TestPropertySource(properties = {
        "app.automation.service-key=" + ApiSecurityTest.SERVICE_KEY,
        "app.auth.success-redirect=http://localhost:5173",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.docs.public=false"
})
class ApiSecurityTest {

    static final String SERVICE_KEY = "test-service-key-long-enough-to-be-real";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationService applications;

    /** Required by SecurityConfig; never invoked, since no test performs a real sign-in. */
    @MockitoBean
    private ProvisioningOidcUserService oidcUserService;

    @Test
    @DisplayName("an anonymous request is refused with 401, not redirected to Google")
    void anonymousIsUnauthorized() throws Exception {
        // A redirect would be worse than useless to the UI: fetch() cannot
        // follow one to accounts.google.com, so an expired session would
        // surface as an opaque CORS failure rather than "please sign in".
        mockMvc.perform(get("/api/v1/applications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a signed-in user gets through")
    void signedInUserIsAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/applications").with(signedInUser()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("/me answers with the workspace the session is bound to")
    void meReturnsTheCurrentWorkspace() throws Exception {
        mockMvc.perform(get("/api/v1/me").with(signedInUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value(7))
                .andExpect(jsonPath("$.email").value("a@example.com"))
                // The Google sub identifies the account permanently and the
                // browser has no use for it, so it must not appear here.
                .andExpect(jsonPath("$.googleSub").doesNotExist());
    }

    @Test
    @DisplayName("a browser write without a CSRF token is refused")
    void writeWithoutCsrfTokenIsForbidden() throws Exception {
        // The session cookie rides along with requests the page did not make.
        // A token that a hostile origin cannot read is what separates a real
        // form submission from a forged one.
        mockMvc.perform(post("/api/v1/applications")
                        .with(signedInUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a browser write with a CSRF token reaches the controller")
    void writeWithCsrfTokenIsAllowed() throws Exception {
        // 400 from validation, not 403 from security: it got past the filters,
        // which is the only thing this test is about.
        mockMvc.perform(post("/api/v1/applications")
                        .with(signedInUser())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a read hands the browser a CSRF token, so the first write is not a wasted 403")
    void csrfTokenIsIssuedBeforeTheFirstWrite() throws Exception {
        // Spring writes the token cookie only when something actually asks for
        // the token, and a safe request never does. Left alone that means a
        // freshly signed-in browser holds no token, its first write is refused,
        // and the refusal is what finally sets the cookie - so clicking again
        // works and the bug reads as intermittent.
        //
        // The test above this one could not catch that: .with(csrf()) puts a
        // valid token on the request directly, which is precisely the step the
        // real browser was missing.
        Cookie token = mockMvc.perform(get("/api/v1/me").with(signedInUser()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(token).isNotNull();

        // The round trip is the real claim: what a read hands out is what a
        // write is accepted with. 400 from validation, not 403 from security.
        mockMvc.perform(post("/api/v1/applications")
                        .with(signedInUser())
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("sign-out succeeds on the first click")
    void logoutSucceedsWithTheTokenAReadHandedOut() throws Exception {
        // /logout is a write, so it needs the same token - and it was failing
        // the same way. The UI reloaded the page regardless of the answer, so a
        // refused sign-out looked like a page that simply blinked and stayed
        // signed in.
        Cookie token = mockMvc.perform(get("/api/v1/me").with(signedInUser()))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");

        assertThat(token).isNotNull();

        mockMvc.perform(post("/logout")
                        .with(signedInUser())
                        .cookie(token)
                        .header("X-XSRF-TOKEN", token.getValue()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("the service key authenticates the worker")
    void serviceKeyIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/applications")
                        .header(ServiceKeyAuthenticationFilter.SERVICE_KEY_HEADER, SERVICE_KEY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a wrong service key is indistinguishable from none at all")
    void wrongServiceKeyIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/applications")
                        .header(ServiceKeyAuthenticationFilter.SERVICE_KEY_HEADER, "not-the-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the worker writes without a CSRF token, because it holds no cookie")
    void serviceKeyWritesAreExemptFromCsrf() throws Exception {
        // Exempt because there is no ambient credential to forge: the worker
        // authenticates per request. Setting the header without the right value
        // buys nothing, as the test above shows.
        mockMvc.perform(post("/api/v1/applications")
                        .header(ServiceKeyAuthenticationFilter.SERVICE_KEY_HEADER, SERVICE_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("answers the browser preflight so the Vite dev server can call the API")
    void corsPreflightIsAllowed() throws Exception {
        // Moved here from the controller slice in 5c. CORS is now handled by the
        // security chain rather than by Spring MVC, and a preflight carries no
        // cookie - so if security did not let OPTIONS through, every request
        // from the UI would fail before it was ever sent.
        mockMvc.perform(options("/api/v1/applications")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                // Without this the browser sends no session cookie, and every
                // call from the UI is anonymous no matter who signed in.
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("an origin that is not on the list is refused")
    void foreignOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/applications")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the OpenAPI document is closed unless the dev profile opens it")
    void apiDocsAreClosedByDefault() throws Exception {
        // A map of every endpoint and field. Harmless on a laptop, and not
        // something to hand an anonymous caller anywhere else - so the default
        // is closed and application-dev.yml is what opens it.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an authenticated caller can read the OpenAPI document either way")
    void apiDocsAreOpenToAnyoneAuthenticated() throws Exception {
        // What keeps the CI contract job working: it starts the API without the
        // dev profile and polls this endpoint with the service key. 404 rather
        // than 200 because springdoc is not part of this slice - the only thing
        // being asserted is that security did not stop the request.
        mockMvc.perform(get("/v3/api-docs")
                        .header(ServiceKeyAuthenticationFilter.SERVICE_KEY_HEADER, SERVICE_KEY))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor signedInUser() {
        WorkspaceUser principal = new WorkspaceUser(
                Mockito.mock(OidcUser.class),
                new SignedInUser(1L, 7L, "a@example.com", "Ada", null));
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    /**
     * oauth2Login needs somewhere to look up "google", and the real repository
     * is built from a client id and secret this test has no business holding.
     */
    @TestConfiguration
    static class ClientRegistrationStub {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            return new InMemoryClientRegistrationRepository(
                    ClientRegistration.withRegistrationId("google")
                            .clientId("test-client-id")
                            .clientSecret("test-client-secret")
                            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                            .scope("openid", "email", "profile")
                            .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                            .tokenUri("https://oauth2.googleapis.com/token")
                            .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                            .userNameAttributeName("sub")
                            .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                            .issuerUri("https://accounts.google.com")
                            .build());
        }
    }
}
