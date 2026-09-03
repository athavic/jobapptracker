package com.jobtracker.auth;

import com.jobtracker.tenancy.SignedInUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.Map;

/**
 * The signed-in principal: what Google said, plus who that is here.
 *
 * <p>Wraps the {@link OidcUser} Spring builds from the ID token and carries the
 * two ids the application actually needs. Spring stores the principal in the
 * security context, which is persisted in the session, so resolving the user
 * and their workspace happens once at sign-in rather than on every request.
 *
 * <p>Only the ids are kept, never the {@code AppUser} entity. An entity held in
 * a session is a detached object that silently goes stale the moment anything
 * updates the row behind it.
 */
public final class WorkspaceUser implements OidcUser {

    private final OidcUser delegate;
    private final SignedInUser signedIn;

    WorkspaceUser(OidcUser delegate, SignedInUser signedIn) {
        this.delegate = delegate;
        this.signedIn = signedIn;
    }

    public Long getUserId() {
        return signedIn.userId();
    }

    public Long getWorkspaceId() {
        return signedIn.workspaceId();
    }

    public String getEmail() {
        return signedIn.email();
    }

    public String getDisplayName() {
        return signedIn.displayName();
    }

    public String getAvatarUrl() {
        return signedIn.avatarUrl();
    }

    /**
     * The Google {@code sub} claim, not the email.
     *
     * <p>This is what Spring logs and what appears in any authentication event,
     * so it is deliberately the stable identifier rather than an address that
     * can be reassigned to somebody else.
     */
    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Map<String, Object> getClaims() {
        return delegate.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return delegate.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return delegate.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return delegate.getAuthorities();
    }
}
