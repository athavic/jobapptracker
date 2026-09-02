package com.jobtracker.common;

import com.jobtracker.automation.RunAlreadyFinishedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into RFC 9457 "problem detail" JSON, so every error the API
 * returns has the same shape. Without this you get Spring's default HTML error
 * page or a stack trace, and the frontend has nothing reliable to parse.
 *
 * <p>The {@code @Order} is not decoration, and it is required by
 * {@code spring.mvc.problemdetails.enabled} rather than merely tidy alongside
 * it. Switching that setting on registers Spring's own advice at
 * {@code @Order(0)}, and that advice handles
 * {@link MethodArgumentNotValidException} too - the exact exception this class
 * handles below in order to attach {@code fieldErrors}. Advice is consulted in
 * order, so without this annotation the default (lowest precedence) puts this
 * class second, Spring's plainer body wins, and every field-level validation
 * message silently stops reaching the form that displays it. Six tests fail
 * when it is removed, which is the cheapest available proof that it is
 * load-bearing. Being explicit lets the two coexist: this class answers what
 * it knows about, Spring answers the rest.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail onNotFound(NotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not found");
        return problem;
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    ProblemDetail onInvalidTransition(InvalidStatusTransitionException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Illegal status transition");
        return problem;
    }

    /**
     * Completing an automation run twice. Also a 409: the request is well formed,
     * it just contradicts the state the resource is already in.
     */
    @ExceptionHandler(RunAlreadyFinishedException.class)
    ProblemDetail onRunAlreadyFinished(RunAlreadyFinishedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Run already finished");
        return problem;
    }

    /** Thrown when @Valid fails on a request body. Report every bad field at once. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidationFailure(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
        problem.setTitle("Validation failed");
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /**
     * Someone else wrote this row between our read and our write.
     *
     * <p>A 409 rather than a 500, and that mapping is the entire reason the
     * version column is worth having. Without it an optimistic-lock failure is
     * an unhandled exception: the caller sees a server error, the worker treats
     * it as the API being broken and fails its whole run, and a human's edit
     * looks like an outage. With it, the answer is "your view of this
     * application was stale" - which nudge_stale already knows how to act on,
     * because it skips 409s and lets the other writer's change stand.
     *
     * <p>Retrying here would be wrong. The caller decided to move to GHOSTED
     * based on a state that is no longer true; the fix is to look again, which
     * only the caller can do.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail onConcurrentModification(OptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "This application was changed by someone else while you were working on it. "
                        + "Reload it and try again.");
        problem.setTitle("Concurrent modification");
        return problem;
    }

    /** A database constraint said no - unique name, check constraint, foreign key. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail onConstraintViolation(DataIntegrityViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "The request conflicts with a database constraint.");
        problem.setTitle("Constraint violation");
        return problem;
    }

    /**
     * Business-rule rejections that are the caller's fault, e.g. salaryMax < salaryMin.
     *
     * <p>Deliberately {@link BusinessRuleException} and not
     * {@code IllegalArgumentException}: see that class for why catching the
     * broader type here turns genuine 500s into misleading 400s.
     */
    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail onBusinessRuleViolation(BusinessRuleException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }
}
