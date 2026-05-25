package com.ai.exception;

/**
 * Raised when a user exceeds token quota.
 *
 * @author data-agent
 */
public class QuotaExceededException extends RuntimeException {
    public QuotaExceededException(String message) {
        super(message);
    }
}
