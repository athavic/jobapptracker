package com.jobtracker.auth;

import com.jobtracker.tenancy.SignInService;
import com.jobtracker.tenancy.SignedInUser;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Where a Google account becomes a row in this database.
 *
 * <p>Spring calls this during the authorization-code exchange, after the ID
 * token is verified and before the principal is stored in the session. Doing
 * the provisioning here rather than in a success handler means there is no
 * moment where someone is authenticated but has no user row: if this throws,
 * the sign-in fails, which is the correct outcome.
 */
@Service
class ProvisioningOidcUserService extends OidcUserService {

    private final SignInService signIns;

    ProvisioningOidcUserService(SignInService signIns) {
        this.signIns = signIns;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser google = super.loadUser(request);

        // 'sub' is guaranteed by OpenID Connect and Spring has already verified
        // the token's signature and issuer by this point. The email is not
        // guaranteed - it depends on the scopes actually granted - and this app
        // cannot address an invitation without one, so its absence is a failed
        // sign-in rather than a user row with a null column.
        String email = google.getEmail();
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_required"),
                    "Google returned no email address for this account");
        }

        SignedInUser signedIn = signIns.signIn(
                google.getSubject(), email, google.getFullName(), google.getPicture());

        return new WorkspaceUser(google, signedIn);
    }
}
