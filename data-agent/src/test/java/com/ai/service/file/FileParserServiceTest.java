package com.ai.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileParserServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesTextFileContent() throws Exception {
        FileParserService service = new FileParserService(List.of(
                new ImageFileContentParser(),
                new PlainTextFileContentParser(),
                new FallbackFileContentParser()));
        Path file = tempDir.resolve("demo.md");
        Files.writeString(file, "# Title\nhello");

        String content = service.parse(file, "demo.md", "text/markdown");

        assertEquals("# Title\nhello", content);
    }

    @Test
    void returnsImagePlaceholderWithoutReadingBinaryAsText() throws Exception {
        FileParserService service = new FileParserService(List.of(
                new ImageFileContentParser(),
                new PlainTextFileContentParser(),
                new FallbackFileContentParser()));
        Path file = tempDir.resolve("image.png");
        Files.write(file, new byte[] {1, 2, 3});

        String content = service.parse(file, "image.png", "image/png");

        assertTrue(content.startsWith("Image file: image.png"));
        assertTrue(content.contains("3 bytes"));
    }

    @Test
    void parsesMultipartTextFileWithPlainTextParser() throws Exception {
        FileParserService service = new FileParserService(List.of(
                new ImageFileContentParser(),
                new PlainTextFileContentParser(),
                new FallbackFileContentParser()));
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "hello world".getBytes());

        String content = service.parse(file);

        assertEquals("hello world", content);
    }

    @Test
    void fallsBackToUtf8TextForUnknownFileTypes() throws Exception {
        FileParserService service = new FileParserService(List.of(
                new ImageFileContentParser(),
                new PlainTextFileContentParser(),
                new FallbackFileContentParser()));
        Path file = tempDir.resolve("binary.bin");
        Files.write(file, "fallback".getBytes());

        String content = service.parse(file, "binary.bin", "application/octet-stream");

        assertEquals("fallback", content);
    }
}
