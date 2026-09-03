package com.jobtracker.tenancy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /**
     * The only way a user is ever looked up. Never by email - see the note on
     * {@link AppUser#getGoogleSub()} for why matching on an address is how one
     * person ends up holding another's applications.
     */
    Optional<AppUser> findByGoogleSub(String googleSub);
}
