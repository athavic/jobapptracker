package com.jobtracker.common;

/**
 * A request the caller got wrong in a way no annotation could catch.
 *
 * <p>This exists so that {@link GlobalExceptionHandler} can answer 400 to a rule
 * this code deliberately enforced, without also answering 400 to every
 * {@code IllegalArgumentException} thrown anywhere beneath it. That distinction
 * matters twice over. A {@code NumberFormatException} from inside Jackson, or an
 * IAE from a Spring internal, is a bug on this side and belongs in a 500 with a
 * stack trace in the log - reporting it as 400 tells the caller to fix a request
 * that was fine, and hides the real failure. And the message of such an
 * exception is written for a developer reading a log, not for a client reading
 * a response body; echoing it back is how internals leak.
 *
 * <p>The message on <em>this</em> exception, by contrast, is written to be read
 * by whoever made the call, and the handler passes it through verbatim.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
