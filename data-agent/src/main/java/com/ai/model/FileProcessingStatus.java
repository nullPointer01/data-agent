package com.ai.model;

/**
 * File processing lifecycle status.
 *
 * @author data-agent
 */
public enum FileProcessingStatus {
    /** File is waiting for asynchronous processing. */
    QUEUED,

    /** File is currently being processed. */
    PROCESSING,

    /** File processing completed successfully. */
    COMPLETED,

    /** File processing failed. */
    FAILED
}
