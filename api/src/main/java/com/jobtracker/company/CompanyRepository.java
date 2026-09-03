package com.jobtracker.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    /**
     * Scoped by workspace, and it has to be: uq_company_workspace_name lets two
     * workspaces each hold a company called Stripe, so a lookup on name alone
     * could now return someone else's row and quietly attach an application to
     * it. Matching lower(name) is what agrees with that index - the unscoped
     * findByNameIgnoreCase disagreed with UNIQUE (name) for exactly as long as
     * it existed.
     */
    Optional<Company> findByWorkspaceIdAndNameIgnoreCase(Long workspaceId, String name);
}
