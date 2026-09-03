package com.jobtracker.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Hands the browser its CSRF token before it needs one.
 *
 * <p>Spring 6 loads the token lazily: CsrfFilter puts a supplier on the request
 * and only resolves it when something calls getToken(), which is also the moment
 * CookieCsrfTokenRepository writes the cookie. A safe request never calls it, so
 * a browser that has only ever read is holding no token at all.
 *
 * <p>The consequence is a bug that looks intermittent rather than broken. Sign
 * in, load the board, submit the first form: refused, because there was no
 * token to send. That refusal resolves the token and sets the cookie, so the
 * second attempt succeeds and nothing appears wrong. Sign-out failed the same
 * way, being a POST like any other.
 *
 * <p>Touching the token on every request is the fix Spring documents for exactly
 * this shape of client. It is not a write on every response: the repository
 * saves the cookie only when the request arrived without one, so this costs a
 * single Set-Cookie per browser, on whichever request comes first.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            // The call is the point, not the value. Resolving the supplier is
            // what persists the cookie.
            token.getToken();
        }

        chain.doFilter(request, response);
    }
}
