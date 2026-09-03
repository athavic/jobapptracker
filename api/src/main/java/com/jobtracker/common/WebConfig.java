package com.jobtracker.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Lets the Vite dev server call this API from the browser.
 *
 * <p>Why this is needed at all: the browser refuses to hand a response from
 * localhost:8080 to a page served from localhost:5173 unless the server says it
 * is allowed. Different port means different origin. curl and Swagger UI never
 * hit this, which is why the first React fetch is always where CORS shows up.
 *
 * <p>Published as a CorsConfigurationSource bean rather than through
 * WebMvcConfigurer.addCorsMappings, and that changed in 5c for a reason: the
 * security filter chain runs before Spring MVC, so an MVC-level CORS mapping
 * never sees the preflight. The browser sends OPTIONS with no cookie, security
 * rejects it as unauthenticated, and every request afterwards fails with a CORS
 * error that has nothing to do with CORS. A bean is what SecurityConfig can
 * read.
 *
 * <p>The origins are a property, not a constant, so a deployment can set
 * CORS_ALLOWED_ORIGINS instead of shipping a build that trusts localhost.
 */
@Configuration
public class WebConfig {

    private final List<String> allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));

        // Required from 5c on: without it the browser sends no session cookie,
        // and every call from the UI is anonymous no matter who is signed in.
        // This is also why allowedOrigins can never become "*" - the browser
        // refuses that combination outright, and rightly so.
        cors.setAllowCredentials(true);

        // Cache the preflight response so the browser stops re-asking before
        // every single request.
        cors.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        // Sign-out lives outside /api because Spring Security owns the path, but
        // the UI calls it the same cross-origin way as everything else. Without
        // this the button fails on CORS and the session outlives the click.
        source.registerCorsConfiguration("/logout", cors);
        return source;
    }
}
