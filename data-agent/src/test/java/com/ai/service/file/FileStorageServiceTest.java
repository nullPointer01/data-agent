package com.ai.service.file;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileStorageServiceTest {

    private static final int MAX_SAFE_FILENAME_LENGTH = 100;

    @TempDir
    private Path uploadDir;

    @Test
    void storeNormalizesPathFilenameAndKeepsFileInsideUploadDirectory() throws Exception {
        FileStorageService service = new FileStorageService(uploadDir.toString());
        MockMultipartFile file = new MockMultipartFile(
                "file", "../dir\\report.txt", "text/plain", "hello".getBytes());

        FileStorageObject storedFile = service.store("file-1", file);

        assertEquals("report.txt", storedFile.filename());
        assertTrue(Path.of(storedFile.path()).startsWith(uploadDir.toAbsolutePath().normalize()));
        assertEquals("hello", Files.readString(Path.of(storedFile.path())));
    }

    @Test
    void storeTruncatesOnlyStorageFilenameButKeepsDisplayFilename() throws Exception {
        FileStorageService service = new FileStorageService(uploadDir.toString());
        String filename = "a".repeat(MAX_SAFE_FILENAME_LENGTH + 10) + ".txt";
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/plain", "hello".getBytes());

        FileStorageObject storedFile = service.store("file-1", file);

        assertEquals(filename, storedFile.filename());
        assertTrue(Path.of(storedFile.path()).getFileName().toString().length()
                <= "file-1_".length() + MAX_SAFE_FILENAME_LENGTH);
    }

    @Test
    void deleteRejectsPathOutsideManagedUploadDirectory() {
        FileStorageService service = new FileStorageService(uploadDir.toString());
        Path outsidePath = uploadDir.resolveSibling("outside.txt");

        assertThrows(SecurityException.class, () -> service.delete(outsidePath.toString()));
        assertFalse(Files.exists(outsidePath));
    }
}
