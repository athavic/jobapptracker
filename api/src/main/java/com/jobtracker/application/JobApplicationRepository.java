package com.jobtracker.application;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface JobApplicationRepository
        extends JpaRepository<JobApplication, Long>, JpaSpecificationExecutor<JobApplication> {

    /**
     * @EntityGraph loads the company in the SAME query. Without it, mapping the
     * response would trigger a second SELECT - and across a list, that is the
     * classic N+1.
     *
     * <p>Spring Data ignores the words between "find" and "By", so the extra
     * "WithCompany" is just documentation for the reader.
     */
    @EntityGraph(attributePaths = "company")
    Optional<JobApplication> findWithCompanyById(Long id);
}
