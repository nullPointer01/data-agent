package com.ai.service.file;

import com.ai.service.AuditLogService;

import com.ai.model.FileMetadata;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileProcessingSubmissionServiceTest {

    @Test
    void submitReturnsAcceptedWhenWorkerAcceptsTask() {
        FileProcessingWorker worker = mock(FileProcessingWorker.class);
        FileMetadataLifecycleService metadataLifecycleService = mock(FileMetadataLifecycleService.class);
        FileProcessingSubmissionService service = new FileProcessingSubmissionService(
                worker, metadataLifecycleService, new SimpleMeterRegistry(), mock(AuditLogService.class));

        FileProcessingSubmissionResult result = service.submit(metadata("file-1"));

        assertTrue(result.accepted());
        verify(worker).processAsync("file-1");
    }

    @Test
    void submitMarksFileFailedWhenWorkerRejectsTask() {
        FileProcessingWorker worker = mock(FileProcessingWorker.class);
        FileMetadataLifecycleService metadataLifecycleService = mock(FileMetadataLifecycleService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        doThrow(new TaskRejectedException("full")).when(worker).processAsync("file-1");
        FileProcessingSubmissionService service = new FileProcessingSubmissionService(
                worker, metadataLifecycleService, new SimpleMeterRegistry(), auditLogService);

        FileProcessingSubmissionResult result = service.submit(metadata("file-1"));

        assertFalse(result.accepted());
        verify(metadataLifecycleService).markRejected("file-1", "文件处理队列已满，请稍后重试");
        verify(auditLogService).record("FILE_PROCESSING_REJECTED", "FILE", "file-1", "FAILED",
                "文件处理队列已满: report.txt");
    }

    private FileMetadata metadata(String fileId) {
        FileMetadata metadata = new FileMetadata();
        metadata.setFileId(fileId);
        metadata.setFilename("report.txt");
        return metadata;
    }
}
