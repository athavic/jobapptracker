package com.jobtracker.auth;

import com.jobtracker.common.BusinessRuleException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Who am I, and which workspace am I in.
 *
 * <p>The endpoint the UI calls on load to decide between rendering the board and
 * showing a sign-in button. It exists because "is there a session?" is not a
 * question a browser can answer for itself: the cookie is HttpOnly, so
 * JavaScript cannot see it, and asking the server is the only honest test.
 */
@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Identity")
class MeController {

    @GetMapping
    @Operation(summary = "The signed-in user and their current workspace")
    MeResponse me(@AuthenticationPrincipal Object principal) {
        if (!(principal instanceof WorkspaceUser user)) {
            // The service key authenticates a machine, which has no profile and
            // no workspace. Saying so beats inventing a shape for it.
            throw new BusinessRuleException("Only a signed-in user has an identity to return");
        }

        return new MeResponse(
                user.getUserId(),
                user.getWorkspaceId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl());
    }
}
