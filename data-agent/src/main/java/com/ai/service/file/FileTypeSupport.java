package com.ai.service.file;

import java.util.Set;

/**
 * Shared file type helpers used by the parsing strategy layer.
 *
 * @author data-agent
 */
final class FileTypeSupport {

    static final int MIN_INDEXABLE_FILE_CONTENT_LENGTH = 50;
    static final String CONTENT_TYPE_IMAGE = "image";
    static final String IMAGE_FILE_PREFIX = "Image file: ";
    static final String UNSUPPORTED_FILE_PREFIX = "Unsupported";
    static final String FILE_EXTENSION_PDF = ".pdf";
    static final String FILE_EXTENSION_XLSX = ".xlsx";
    static final String FILE_EXTENSION_XLS = ".xls";
    static final String FILE_EXTENSION_DOCX = ".docx";
    static final String FILE_EXTENSION_PPTX = ".pptx";
    static final String FILE_NAME_DOCKERFILE = "dockerfile";
    static final String FILE_NAME_MAKEFILE = "makefile";
    static final String FILE_NAME_DOCKERIGNORE = "dockerignore";
    static final String FILE_NAME_GITIGNORE = "gitignore";
    static final String FILE_NAME_NPMIGNORE = "npmignore";
    static final String FILE_NAME_EDITORCONFIG = "editorconfig";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".tif", ".tiff", ".ico", ".heic", ".heif", ".avif",
            ".svg"
    );

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".csv", ".md", ".json", ".xml", ".log", ".html", ".htm",
            ".yaml", ".yml", ".properties", ".ini", ".conf", ".cfg", ".toml",
            ".py", ".java", ".js", ".ts", ".jsx", ".tsx", ".vue", ".css",
            ".scss", ".sass", ".less", ".sql", ".go", ".rs", ".sh", ".php",
            ".rb", ".c", ".cpp", ".h", ".hpp", ".gradle", ".groovy", ".scala",
            ".kt", ".kts", ".bat", ".ps1", ".cmd", ".dockerfile", ".makefile",
            ".proto", ".graphql", ".rst", ".asciidoc", ".adoc", ".tex", ".rtf",
            ".textile", ".env", ".dockerignore", ".gitignore", ".npmignore",
            ".editorconfig", ".lock", ".sum", ".mod", ".svg"
    );

    private FileTypeSupport() {
    }

    /**
     * Normalizes a filename to lower case, tolerating null input.
     *
     * @param filename original filename
     * @return lower-case filename, or empty string when absent
     */
    static String lowerName(String filename) {
        return filename == null ? "" : filename.toLowerCase();
    }

    /**
     * Checks whether a content type points to an image.
     *
     * @param contentType mime type
     * @return true when the mime type is an image
     */
    static boolean isImage(String contentType) {
        return contentType != null && contentType.contains(CONTENT_TYPE_IMAGE);
    }

    /**
     * Checks whether a filename points to a common image extension.
     *
     * @param lowerName normalized lower-case filename
     * @return true when the filename looks like an image
     */
    static boolean isImageFile(String lowerName) {
        return IMAGE_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }

    /**
     * Checks whether a filename should be treated as plain text.
     *
     * @param lowerName normalized lower-case filename
     * @return true when the file should be read as text
     */
    static boolean isTextFile(String lowerName) {
        return TEXT_EXTENSIONS.stream().anyMatch(lowerName::endsWith)
                || FILE_NAME_DOCKERFILE.equals(lowerName)
                || FILE_NAME_MAKEFILE.equals(lowerName)
                || FILE_NAME_DOCKERIGNORE.equals(lowerName)
                || FILE_NAME_GITIGNORE.equals(lowerName)
                || FILE_NAME_NPMIGNORE.equals(lowerName)
                || FILE_NAME_EDITORCONFIG.equals(lowerName);
    }

    /**
     * Builds a human-readable placeholder for images.
     *
     * @param filename image name
     * @param size image size in bytes
     * @return placeholder text
     */
    static String imagePlaceholder(String filename, long size) {
        return IMAGE_FILE_PREFIX + filename + " (" + size + " bytes)";
    }
}
