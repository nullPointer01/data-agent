package com.ai.memory.dto;

/**
 * Response for memory mutation commands.
 *
 * @param success whether command succeeded
 * @param message command message
 * @param affectedCount affected memory count
 * @author data-agent
 */
public record MemoryMutationResponse(boolean success, String message, int affectedCount) {

    /**
     * Creates a successful mutation response.
     *
     * @param message response message
     * @param affectedCount affected count
     * @return response
     */
    public static MemoryMutationResponse success(String message, int affectedCount) {
        return new MemoryMutationResponse(true, message, affectedCount);
    }

    /**
     * Creates a failure mutation response.
     *
     * @param message response message
     * @return response
     */
    public static MemoryMutationResponse failure(String message) {
        return new MemoryMutationResponse(false, message, 0);
    }
}
