package com.jobtracker.tenancy;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    /**
     * Reads a workspace and holds the row until the transaction ends.
     *
     * <p>Used for exactly one thing: deciding whether the bootstrap workspace
     * has been claimed yet. Without the lock, two people signing in for the
     * very first time at the same moment can both read "unclaimed" and both
     * join it - which would put a stranger inside someone else's applications,
     * the precise failure the whole tenancy model exists to prevent. The window
     * is milliseconds wide and will realistically never open; it is locked
     * anyway because the cost is one annotation and the consequence is the bad
     * one.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Workspace> findWithLockById(Long id);
}
