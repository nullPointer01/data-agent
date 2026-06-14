package com.ai.service.file;

import org.springframework.context.ApplicationEvent;

/**
 * 文件上传并解析后发布的域事件。
 *
 * @author data-agent
 */
public class FileUploadedEvent extends ApplicationEvent {

    private final String fileId;
    private final String filename;
    private final String content;

    public FileUploadedEvent(Object source, String fileId, String filename, String content) {
        super(source);
        this.fileId = fileId;
        this.filename = filename;
        this.content = content;
    }

    public String getFileId() {
        return fileId;
    }

    public String getFilename() {
        return filename;
    }

    public String getContent() {
        return content;
    }
}
