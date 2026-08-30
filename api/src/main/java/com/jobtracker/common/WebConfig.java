package com.jobtracker.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Lets the Vite dev server call this API from the browser.
 *
 * <p>Why this is needed at all: the browser refuses to hand a response from
 * localhost:8080 to a page served from localhost:5173 unless the server says it
 * is allowed. Different port means different origin. curl and Swagger UI never
 * hit this, which is why the first React fetch is always where CORS shows up.
 *
 * <p>The origins are a property, not a constant, so a deployment can set
 * CORS_ALLOWED_ORIGINS instead of shipping a build that trusts localhost.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                // Cache the preflight response so the browser stops re-asking
                // before every single request.
                .maxAge(3600);
    }
}
