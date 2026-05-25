package com.ai.service.file;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Parses plain text and unknown files by reading them as UTF-8 text.
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
