package com.ai.service.file;

/**
 * Result of submitting one file to the asynchronous processing executor.
 *
 * @param accepted whether the executor accepted the task
 * @param message user-facing submission message
 * @author data-agent
 */
public record FileProcessingSubmissionResult(boolean accepted, String message) {

    /**
     * Creates an accepted submission result.
     *
     * @return accepted result
     */
    public static FileProcessingSubmissionResult acceptedResult() {
        return new FileProcessingSubmissionResult(true, null);
    }

    /**
     * Creates a rejected submission result.
     *
     * @param message rejection reason
     * @return rejected result
     */
    public static FileProcessingSubmissionResult rejectedResult(String message) {
        return new FileProcessingSubmissionResult(false, message);
    }
}
