package com.jobtracker.common;

import com.jobtracker.automation.RunAlreadyFinishedException;
import org.springframework.dao.DataIntegrityViolationException;
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
 */
@RestControllerAdvice
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
