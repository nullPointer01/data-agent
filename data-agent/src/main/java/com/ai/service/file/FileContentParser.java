package com.ai.service.file;

/**
 * 将一种文件类型解析为文本内容的策略。
 *
 * @author data-agent
 */
public interface FileContentParser {

    /**
     * 检查此解析器是否可以处理给定文件。
     *
     * @param context 规范化文件上下文
     * @return 解析器可以处理该文件时返回 true
     */
    boolean supports(FileParsingContext context);

    /**
     * 将文件解析为文本。
     *
     * @param context 规范化文件上下文
     * @return 解析的文本内容
     * @throws Exception 当文件无法解析时抛出异常
     */
    String parse(FileParsingContext context) throws Exception;
}
