package com.jobtracker.auth;

import com.jobtracker.common.BusinessRuleException;
import com.jobtracker.common.WorkspaceContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The workspace comes from whoever is signed in.
 *
 * <p>Replaces FixedWorkspaceContext, which named a workspace in configuration
 * because 5b had no principal to ask. This is the thing that placeholder
 * existed to become.
 *
 * <p>Note what happens for the service principal: it throws. The worker has no
 * workspace, and the alternative - falling back to some default - would be a
 * background job silently writing a company into a tenant nobody chose. Nothing
 * the worker does needs this: it changes the status of applications whose ids
 * it was given, and those already know which workspace they belong to.
 */
@Component
class AuthenticatedWorkspaceContext implements WorkspaceContext {

    @Override
    public Long currentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof WorkspaceUser user) {
            return user.getWorkspaceId();
        }

        // Reached only by a caller that authenticated some other way - today
        // that means the service key. A 400 rather than a 500 because the
        // request is answerable, just not by this caller.
        throw new BusinessRuleException(
                "This operation writes into a workspace and must be made by a signed-in user");
    }
}
