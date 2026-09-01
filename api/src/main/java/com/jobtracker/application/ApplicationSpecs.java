package com.jobtracker.application;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;

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
