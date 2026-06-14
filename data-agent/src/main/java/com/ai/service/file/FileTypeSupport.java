package com.ai.service.file;

import java.util.Set;

/**
 * 解析策略层使用的共享文件类型辅助程序。
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
     * 将文件名规范化为小写，容忍空输入。
     *
     * @param filename 原始文件名
     * @return 小写文件名，缺失时返回空字符串
     */
    static String lowerName(String filename) {
        return filename == null ? "" : filename.toLowerCase();
    }

    /**
     * 检查内容类型是否指向图像。
     *
     * @param contentType MIME 类型
     * @return MIME 类型为图像时返回 true
     */
    static boolean isImage(String contentType) {
        return contentType != null && contentType.contains(CONTENT_TYPE_IMAGE);
    }

    /**
     * 检查文件名是否指向常见的图像扩展名。
     *
     * @param lowerName 规范化的小写文件名
     * @return 文件名看起来像图像时返回 true
     */
    static boolean isImageFile(String lowerName) {
        return IMAGE_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }

    /**
     * 检查文件名是否应被视为纯文本。
     *
     * @param lowerName 规范化的小写文件名
     * @return 文件应被读为文本时返回 true
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
     * 为图像构建易读的占位符。
     *
     * @param filename 图像名称
     * @param size 图像大小（字节）
     * @return 占位符文本
     */
    static String imagePlaceholder(String filename, long size) {
        return IMAGE_FILE_PREFIX + filename + " (" + size + " bytes)";
    }
}
