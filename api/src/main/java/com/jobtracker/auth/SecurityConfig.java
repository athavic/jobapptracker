package com.jobtracker.auth;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Phase 5c: the API stops being open.
 *
 * <p>Everything under /api is now behind a principal, which arrives one of two
 * ways - a person who signed in with Google and carries a session cookie, or
 * the Python worker holding the service key. There is no third way and no
 * anonymous fallback: adding spring-boot-starter-security denies by default,
 * and every exception below is written down rather than inherited.
 *
 * <p>What this deliberately does NOT do is filter data. Every signed-in user
 * still sees every application, exactly as before. Authentication and scoping
 * are separate problems, and 5d is where the base Specification arrives with a
 * leak test written first and watched to fail. Landing both here would mean
 * debugging "who are you" and "what may you see" in the same commit, and only
 * one of those fails loudly when it is wrong.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    private static final String[] DOCS_PATHS = {
            "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"};

    private final CorsConfigurationSource cors;
    private final OidcUserService oidcUserService;
    private final String serviceKey;
    private final String successRedirect;
    private final boolean docsPublic;

    SecurityConfig(
                   // By name, because HandlerMappingIntrospector also implements
                   // CorsConfigurationSource - injecting by type alone finds two
                   // beans and fails at startup with a message that does not
                   // mention CORS at all.
                   @Qualifier("corsConfigurationSource") CorsConfigurationSource cors,
                   ProvisioningOidcUserService oidcUserService,
                   @Value("${app.automation.service-key}") String serviceKey,
                   @Value("${app.auth.success-redirect}") String successRedirect,
                   @Value("${app.docs.public}") boolean docsPublic) {
        this.cors = cors;
        this.oidcUserService = oidcUserService;
        this.serviceKey = serviceKey;
        this.successRedirect = successRedirect;
        this.docsPublic = docsPublic;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(configurer -> configurer.configurationSource(cors))

            .authorizeHttpRequests(auth -> auth
                    // The sign-in handshake itself, which by definition happens
                    // before there is anyone to authenticate.
                    .requestMatchers("/oauth2/**", "/login/**").permitAll()
                    // Spring forwards to /error internally; blocking it turns
                    // every error into a second, more confusing error.
                    .requestMatchers("/error").permitAll()
                    // The OpenAPI document and Swagger UI: open to anyone when
                    // the dev profile says so, and otherwise to anyone who has
                    // authenticated. The document carries no data, but it is a
                    // precise map of every endpoint and field, and there is no
                    // reason to hand that to an anonymous caller in production.
                    // `npm run generate:api` is what needs it credential-free,
                    // and that only ever runs locally.
                    .requestMatchers(DOCS_PATHS).access((authentication, context) ->
                            new AuthorizationDecision(docsPublic || isAuthenticated(authentication.get())))
                    .anyRequest().authenticated())

            .oauth2Login(login -> login
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(oidcUserService))
                    // Back to the UI, not to whatever page was being fetched
                    // when the session expired. The API and the app are on
                    // different origins in development, so the default
                    // "continue where you left off" would land the browser on a
                    // JSON endpoint.
                    .defaultSuccessUrl(successRedirect, true))

            .logout(logout -> logout
                    .logoutSuccessHandler((request, response, authentication) ->
                            response.setStatus(HttpStatus.NO_CONTENT.value()))
                    .deleteCookies("JSESSIONID"))

            .exceptionHandling(handling -> handling
                    // 401, never a redirect to Google. A fetch() cannot follow a
                    // redirect to accounts.google.com - it fails as an opaque
                    // CORS error that says nothing about the session having
                    // expired. A status code the UI can read is the difference
                    // between "please sign in" and an unexplained failure.
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

            .sessionManagement(session -> session
                    // A session is created at sign-in and at no other time. The
                    // worker authenticates per request and has no business
                    // being given one.
                    .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))

            .csrf(csrf -> csrf
                    // The token goes in a cookie the UI can read and echo back
                    // in a header. Cookie authentication means the browser
                    // attaches credentials to requests the page did not make;
                    // a header the attacker's origin cannot set is what
                    // distinguishes the two.
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    // The worker is exempt because it is not a browser: it holds
                    // no cookie, so there is no ambient credential to abuse.
                    // Note that merely setting the header buys an attacker
                    // nothing - a custom header forces a CORS preflight, which
                    // the origin list above refuses.
                    .ignoringRequestMatchers(request ->
                            request.getHeader(ServiceKeyAuthenticationFilter.SERVICE_KEY_HEADER) != null))

            .addFilterBefore(new ServiceKeyAuthenticationFilter(serviceKey),
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Whether the request carries a real principal.
     *
     * <p>An anonymous request still has an Authentication - the anonymous one,
     * whose isAuthenticated() returns true. Checking that alone would let
     * everybody through, which is the trap this method exists to avoid.
     */
    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
