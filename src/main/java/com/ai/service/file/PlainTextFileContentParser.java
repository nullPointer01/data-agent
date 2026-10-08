package com.ai.service.file;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import java.io.InputStream;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 官方 TextDocumentParser 读取已识别的 UTF-8 文本；未知二进制仍由拒绝策略处理。
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
        try (InputStream input = context.openInputStream()) {
            return new TextDocumentParser().parse(input).text();
        } catch (BlankDocumentException blank) {
            return "";
        }
    }
}
