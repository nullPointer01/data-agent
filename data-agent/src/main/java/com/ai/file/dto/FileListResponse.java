package com.ai.file.dto;

import java.util.List;

/**
 * File list response.
 *
 * @author data-agent
 */
public record FileListResponse(boolean success, List<FileResponse> files) {
}
