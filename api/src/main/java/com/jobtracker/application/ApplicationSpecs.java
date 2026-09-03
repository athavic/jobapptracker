package com.jobtracker.application;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.Optional;

/**
 * Composable filters for the list endpoint.
 *
 * <p>Why Specifications instead of one big JPQL query with "(:status is null or
 * ...)" everywhere: an unset filter here adds no SQL at all, rather than adding
 * a null-typed parameter that PostgreSQL then has to guess a type for.
 */
final class ApplicationSpecs {

    private ApplicationSpecs() {
    }

    /**
     * The base filter: every query in this service is anded with it.
     *
     * <p>An empty scope adds no predicate at all, which means the caller reads
     * every workspace. That is correct for exactly one caller - the Python
     * worker, which scans across all of them - and catastrophic for any other,
     * which is why the scope arrives as an Optional that has to be unwrapped
     * rather than a Long that could be null by accident.
     */
    static Specification<JobApplication> inWorkspace(Optional<Long> workspaceId) {
        return (root, query, cb) -> workspaceId
                .map(id -> cb.equal(root.get("workspaceId"), id))
                .orElseGet(cb::conjunction);
    }

    /**
     * Matches one application by id.
     *
     * <p>Exists so that a single-row lookup goes through the same Specification
     * chain as the list. Fetching by id and then comparing the workspace in Java
     * would work, but it is a check somebody can forget at a new call site,
     * whereas a query that cannot be built without a scope is not forgettable.
     */
    static Specification<JobApplication> hasId(Long id) {
        return (root, query, cb) -> cb.equal(root.get("id"), id);
    }

    /**
     * Matches any of the given statuses, or every status when none are given.
     *
     * <p>An empty collection means "no filter", not "match nothing". Both readings
     * are defensible in the abstract; this one is right here because the empty
     * case is what the UI sends when you untick the last checkbox, and a filter
     * that hides everything the moment you clear it reads as a broken page.</p>
     */
    static Specification<JobApplication> hasStatusIn(Collection<ApplicationStatus> statuses) {
        return (root, query, cb) -> statuses == null || statuses.isEmpty()
                ? cb.conjunction()
                : root.get("status").in(statuses);
    }

    static Specification<JobApplication> companyNameContains(String fragment) {
        return (root, query, cb) -> {
            if (fragment == null || fragment.isBlank()) {
                return cb.conjunction();
            }
            return cb.like(
                    cb.lower(root.get("company").get("name")),
                    "%" + fragment.toLowerCase() + "%");
        };
    }

    static Specification<JobApplication> archivedFilter(boolean includeArchived) {
        return (root, query, cb) ->
                includeArchived ? cb.conjunction() : cb.isFalse(root.get("archived"));
    }

    /**
     * Fetch the company alongside the applications, so rendering the list is one
     * query instead of one-per-row.
     *
     * <p>The result-type check matters: Spring Data runs a separate COUNT query for
     * paging, and a fetch join is illegal there.
     */
    static Specification<JobApplication> fetchCompany() {
        return (root, query, cb) -> {
            if (query != null && !Long.class.equals(query.getResultType())) {
                root.fetch("company", JoinType.INNER);
            }
            return cb.conjunction();
        };
    }
}
