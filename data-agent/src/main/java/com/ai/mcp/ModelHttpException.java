package com.ai.mcp;

/**
 * HTTP model endpoint exception with retry semantics.
 *
 * @author data-agent
 */
public class ModelHttpException extends IllegalStateException {

    private final int statusCode;
    private final boolean retriable;

    public ModelHttpException(int statusCode, String message, boolean retriable) {
        super(message);
        this.statusCode = statusCode;
        this.retriable = retriable;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetriable() {
        return retriable;
    }
}
