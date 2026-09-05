package com.ai.service.file;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 将上传的文件解析为文本内容以供索引和分析。
 *
 * @author data-agent
 */
@Service
public class FileParserService {

    private final List<FileContentParser> fileContentParsers;

    public FileParserService(List<FileContentParser> fileContentParsers) {
        List<FileContentParser> parsers = new ArrayList<>(fileContentParsers);
        AnnotationAwareOrderComparator.sort(parsers);
        this.fileContentParsers = List.copyOf(parsers);
    }

    /**
     * 将存储的文件解析为文本。
     *
     * @param path 存储文件路径
     * @param filename 原始文件名
     * @param contentType MIME 类型
     * @return 解析的内容
     * @throws Exception 解析失败时抛出异常
     */
    public String parse(Path path, String filename, String contentType) throws Exception {
        return parse(FileParsingContext.from(path, filename, contentType));
    }

    /**
     * 将多部分上传解析为文本。
     *
     * @param file 多部分上传
     * @return 解析的内容
     * @throws Exception 解析失败时抛出异常
     */
    public String parse(MultipartFile file) throws Exception {
        return parse(FileParsingContext.from(file));
    }

    /**
     * Checks whether the parsed content is useful for downstream reasoning.
     *
     * @param content parsed content
     * @return true when the content can be read by downstream services
     */
    public boolean isReadableContent(String content) {
        return content != null
                && !content.isBlank()
                && !content.startsWith(FileTypeSupport.IMAGE_FILE_PREFIX)
                && !content.startsWith(FileTypeSupport.UNSUPPORTED_FILE_PREFIX);
    }

    /**
     * Checks whether the parsed content is large enough to be indexed.
     *
     * @param content parsed content
     * @return true when the content is large enough for indexing
     */
    public boolean isIndexableFileContent(String content) {
        return isReadableContent(content) && content.length() > FileTypeSupport.MIN_INDEXABLE_FILE_CONTENT_LENGTH;
    }

    /**
     * Exposes the configured parser list for focused tests.
     *
     * @return parser list in evaluation order
     */
    List<FileContentParser> getFileContentParsers() {
        return fileContentParsers;
    }

    /**
     * Resolves the first parser that supports the given file and delegates parsing to it.
     *
     * @param context normalized file context
     * @return parsed content
     * @throws Exception when no parser accepts the file or parsing fails
     */
    private String parse(FileParsingContext context) throws Exception {
        for (FileContentParser parser : fileContentParsers) {
            if (parser.supports(context)) {
                return parser.parse(context);
            }
        }
        throw new IllegalStateException("No parser found for file: " + context.filename());
    }
}
