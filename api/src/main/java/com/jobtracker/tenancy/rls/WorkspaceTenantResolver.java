package com.jobtracker.tenancy.rls;

import com.jobtracker.auth.ServiceKeyAuthenticationFilter;
import com.jobtracker.auth.WorkspaceUser;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Turns the authenticated caller into the value V8's policies compare against.
 *
 * <p>Three answers, and the third is the one that matters:
 *
 * <ul>
 *   <li>a signed-in user - the id of their workspace
 *   <li>the Python worker - {@link #ALL_WORKSPACES}, the sentinel V8's USING
 *       clause accepts and its WITH CHECK clause does not
 *   <li>anything else - {@link #NO_WORKSPACE}, which is not a number, so
 *       app_current_workspace() returns NULL and every policy denies
 * </ul>
 *
 * <p><b>Why this does not simply reuse WorkspaceContext.readScope().</b> That
 * method returns an empty Optional for the worker, and empty means "no filter".
 * It arrives at empty by elimination - the principal is not a WorkspaceUser -
 * which was safe under 5d because SecurityConfig rejects anonymous callers
 * before a service method runs. Reused here it would be a trapdoor: any future
 * path that reaches Hibernate without a principal, a scheduled task or a health
 * check that touches an entity, would resolve to "no filter" and read every
 * workspace in the system. So this class decides by what a caller <i>has</i> -
 * ROLE_SERVICE, granted only by a filter that verified the service key - and
 * treats everything it does not recognise as entitled to nothing.
 *
 * <p>The two must agree, and they are checked against each other in
 * RowLevelSecurityTest.
 */
@Component
public class WorkspaceTenantResolver implements CurrentTenantIdentifierResolver<String> {

    /**
     * Read every workspace. Matched literally by V8's USING clause.
     *
     * <p>Not a number, deliberately. If this were, say, "0" then an off-by-one
     * or a default-initialised long would land on it, and the value that means
     * "all tenants" is the last one that should be reachable by accident.
     */
    public static final String ALL_WORKSPACES = "all";

    /**
     * Read nothing. Any non-numeric string would do - app_current_workspace()
     * returns NULL for all of them - but naming it makes the intent legible in
     * a stack trace and in the query log.
     */
    public static final String NO_WORKSPACE = "none";

    @Override
    public String resolveCurrentTenantIdentifier() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null) {
            return NO_WORKSPACE;
        }

        if (authentication.getPrincipal() instanceof WorkspaceUser user) {
            return String.valueOf(user.getWorkspaceId());
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (ServiceKeyAuthenticationFilter.ROLE_SERVICE.equals(authority.getAuthority())) {
                return ALL_WORKSPACES;
            }
        }

        return NO_WORKSPACE;
    }

    /**
     * False, because the tenant of an open session never changes here.
     *
     * <p>True would make Hibernate re-resolve and throw if the answer moved
     * mid-session. Nothing in this application changes principal inside a
     * transaction, and the check costs a resolve per session, so it buys an
     * error for a situation that cannot arise.
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }
}
