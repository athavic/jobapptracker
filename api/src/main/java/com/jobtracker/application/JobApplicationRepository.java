package com.jobtracker.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Deliberately empty.
 *
 * <p>findWithCompanyById lived here until 5d and was removed rather than left
 * unused. It looked up an application by id and nothing else, which is exactly
 * the call that leaks one workspace's row to another - and an unscoped lookup
 * sitting on the repository is an invitation for the next person to use it.
 * Everything now goes through ApplicationService.load, which cannot build a
 * query without a scope. The company is still fetched in the same SELECT, by
 * ApplicationSpecs.fetchCompany.
 *
 * <p>JpaSpecificationExecutor is what supplies findOne and exists, both of which
 * take the Specification that carries the scope.
 */
public interface JobApplicationRepository
        extends JpaRepository<JobApplication, Long>, JpaSpecificationExecutor<JobApplication> {
}
