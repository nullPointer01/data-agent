package com.ai.service;

import org.springframework.context.ApplicationEvent;

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

    public String getFileId() { return fileId; }
    public String getFilename() { return filename; }
    public String getContent() { return content; }
}
