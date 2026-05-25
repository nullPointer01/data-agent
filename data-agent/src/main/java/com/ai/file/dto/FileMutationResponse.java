package com.ai.file.dto;

/**
 * File mutation response.
 *
 * @author data-agent
 */
public record FileMutationResponse(
        boolean success,
        String message,
        String fileId) {

    public static FileMutationResponse deleted() {
        return new FileMutationResponse(true, "文件删除成功", null);
    }

    public static FileMutationResponse failure(String message) {
        return new FileMutationResponse(false, message, null);
    }
}
