package com.ai.service.file;

import com.ai.service.VectorMemoryService;

import com.ai.rag.fulltext.FullTextIndexService;
import com.ai.vector.VectorDocumentTypes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileVectorCleanupServiceTest {

    @Test
    void removeFileVectorsDeletesVectorAndFullTextIndexes() {
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        FullTextIndexService fullTextIndexService = mock(FullTextIndexService.class);
        FileVectorCleanupService service = new FileVectorCleanupService(vectorMemoryService, fullTextIndexService);

        service.removeFileVectors("file-1", "tenant-1");

        verify(vectorMemoryService).removeFromStore(VectorDocumentTypes.FILE, "file-1", "tenant-1");
        verify(fullTextIndexService).deleteBySource(VectorDocumentTypes.FILE, "file-1", "tenant-1");
    }
}
