package com.ai.agent.tool;

import com.ai.file.dto.FileListResponse;
import com.ai.file.dto.FileResponse;
import com.ai.service.file.FileProcessingService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentFileToolServiceTest {

    private static final int LONG_CONTENT_LENGTH = 3100;

    @Test
    void listFilesFormatsFileResponses() {
        FileProcessingService fileProcessingService = mock(FileProcessingService.class);
        FileResponse file = new FileResponse(
                true, "file-1", "orders.csv", "text/csv", 0L, null, "COMPLETED", null, null, null);
        when(fileProcessingService.listFiles()).thenReturn(new FileListResponse(true, List.of(file)));

        AgentFileToolService service = new AgentFileToolService(fileProcessingService);

        String result = service.listFiles();

        assertTrue(result.contains("file-1"));
        assertTrue(result.contains("orders.csv"));
        assertTrue(result.contains("text/csv"));
    }

    @Test
    void getFileContentTruncatesLongContent() {
        FileProcessingService fileProcessingService = mock(FileProcessingService.class);
        when(fileProcessingService.getFileContent("file-1")).thenReturn("a".repeat(LONG_CONTENT_LENGTH));

        AgentFileToolService service = new AgentFileToolService(fileProcessingService);

        String result = service.getFileContent("file-1");

        assertTrue(result.contains("文件已截断"));
        assertTrue(result.contains(String.valueOf(LONG_CONTENT_LENGTH)));
        assertFalse(result.length() >= LONG_CONTENT_LENGTH);
    }

    @Test
    void analyzeFileDataSummarizesNumericCsvColumns() {
        FileProcessingService fileProcessingService = mock(FileProcessingService.class);
        when(fileProcessingService.getFileContent("file-1")).thenReturn("""
                name,amount,count
                A,10.5,2
                B,20.5,3
                C,text,4
                """);

        AgentFileToolService service = new AgentFileToolService(fileProcessingService);

        String result = service.analyzeFileData("file-1");

        assertTrue(result.contains("- 总行数: 4"));
        assertTrue(result.contains("- 列数: 3"));
        assertTrue(result.contains("**amount**: 有效数据2条, 求和=31.00, 均值=15.50, 最小=10.50, 最大=20.50"));
        assertTrue(result.contains("**count**: 有效数据3条, 求和=9.00, 均值=3.00, 最小=2.00, 最大=4.00"));
    }
}
