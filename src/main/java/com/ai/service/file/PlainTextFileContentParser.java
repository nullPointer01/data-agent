package com.ai.service.file;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 通过以 UTF-8 文本形式读取纯文本和未知文件来解析它们。
 *
 * @author data-agent
 */
@Component
@Order(100)
public class PlainTextFileContentParser implements FileContentParser {

    @Override
    public boolean supports(FileParsingContext context) {
        return FileTypeSupport.isTextFile(context.lowerFilename());
    }

    @Override
    public String parse(FileParsingContext context) throws Exception {
        return context.readText();
    }
}
