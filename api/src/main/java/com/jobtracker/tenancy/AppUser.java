package com.jobtracker.tenancy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A person, identified by their Google account.
 *
 * <p>Named app_user because "user" is a reserved word in PostgreSQL, and a table
 * you can only address in quotes is a papercut forever.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Google's {@code sub} claim: opaque, and permanent for that account.
     *
     * <p>This is the identity, and {@link #email} is not. On a Workspace domain
     * an address can be reassigned to a new person after the original leaves, so
     * an app that matches on email hands a new hire their predecessor's
     * applications. Never updated - a row whose sub changed would be a different
     * person wearing the same id.
     */
    @Column(name = "google_sub", nullable = false, updatable = false, length = 255)
    private String googleSub;

    /** Mutable on purpose: people change addresses, and this one is not the key. */
    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public AppUser(String googleSub, String email) {
        this.googleSub = googleSub;
        this.email = email;
    }

    /**
     * Refreshes what Google says about this person on each sign-in.
     *
     * <p>Deliberately does not touch googleSub. Everything here is a display
     * detail that can legitimately differ from one sign-in to the next; the
     * identity is the one thing that must not.
     */
    public void refreshProfile(String email, String displayName, String avatarUrl) {
        this.email = email;
        this.displayName = displayName;
        this.avatarUrl = avatarUrl;
    }

    public Long getId() {
        return id;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }
}
