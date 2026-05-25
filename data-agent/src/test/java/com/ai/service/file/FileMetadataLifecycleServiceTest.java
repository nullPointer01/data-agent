package com.ai.service.file;

import com.ai.model.FileMetadata;
import com.ai.model.FileProcessingStatus;
import com.ai.repository.FileMetadataRepository;
import com.ai.security.SecurityContextHelper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileMetadataLifecycleServiceTest {

    @Test
    void createQueuedAttachesCurrentTenantAndUser() {
        FileMetadataRepository repository = mock(FileMetadataRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(securityContextHelper.getCurrentUserId()).thenReturn("user-1");
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(FileMetadata.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        FileMetadataLifecycleService service = new FileMetadataLifecycleService(repository, securityContextHelper);

        FileMetadata metadata = service.createQueued(new FileStorageObject(
                "file-1", "report.txt", "text/plain", 12L, "/tmp/report.txt"));

        assertEquals("tenant-1", metadata.getTenantId());
        assertEquals("user-1", metadata.getUploadedBy());
        assertEquals(FileProcessingStatus.QUEUED, metadata.getProcessingStatus());
        verify(repository).saveAndFlush(metadata);
    }

    @Test
    void markCompletedStoresContentAndClearsProcessingError() {
        FileMetadataRepository repository = mock(FileMetadataRepository.class);
        FileMetadata metadata = new FileMetadata();
        metadata.setFileId("file-1");
        metadata.setProcessingError("old error");
        when(repository.findById("file-1")).thenReturn(Optional.of(metadata));
        when(repository.save(metadata)).thenReturn(metadata);
        FileMetadataLifecycleService service = new FileMetadataLifecycleService(
                repository, mock(SecurityContextHelper.class));

        service.markCompleted("file-1", "parsed");

        assertEquals("parsed", metadata.getContent());
        assertEquals(FileProcessingStatus.COMPLETED, metadata.getProcessingStatus());
        assertNull(metadata.getProcessingError());
        assertNotNull(metadata.getProcessedAt());
        verify(repository).save(metadata);
    }
}
