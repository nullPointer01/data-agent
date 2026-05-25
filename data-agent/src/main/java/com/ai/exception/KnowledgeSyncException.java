package com.ai.exception;

/**
 * Exception thrown when external knowledge synchronization fails.
 *
 * @author data-agent
 */
public class KnowledgeSyncException extends RuntimeException {

    public KnowledgeSyncException(String message) {
        super(message);
    }

    public KnowledgeSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
