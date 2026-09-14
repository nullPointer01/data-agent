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
     * 判断解析结果是否可供下游推理使用。
     *
     * @param content 解析后的文本
     * @return 下游可以读取时返回 true
     */
    public boolean isReadableContent(String content) {
        return content != null
                && !content.isBlank();
    }

    /**
     * 判断解析结果是否达到向量和全文索引的最小长度。
     *
     * @param content 解析后的文本
     * @return 内容达到索引条件时返回 true
     */
    public boolean isIndexableFileContent(String content) {
        return isReadableContent(content) && content.length() > FileTypeSupport.MIN_INDEXABLE_FILE_CONTENT_LENGTH;
    }

    /**
     * 返回按优先级排序的解析器列表，供定向测试验证。
     *
     * @return 解析器执行顺序
     */
    List<FileContentParser> getFileContentParsers() {
        return fileContentParsers;
    }

    /**
     * 选择第一个支持当前文件的解析器并执行解析。
     *
     * @param context 规范化文件上下文
     * @return 解析后的文本
     * @throws Exception 没有解析器支持或解析失败时抛出
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
