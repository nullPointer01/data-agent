package com.ai.service.file;

import org.springframework.context.ApplicationEvent;

/**
 * Domain event published after a file is uploaded and parsed.
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
