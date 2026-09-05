package com.ai.file.dto;

import java.util.List;

/**
 * 文件列表响应。
 *
 * @author data-agent
 */
public record FileListResponse(boolean success, List<FileResponse> files) {
}
