package com.ai.service.file;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Parses image uploads into a lightweight placeholder instead of raw binary text.
 *
 * @author data-agent
 */
@Component
@Order(0)
public class ImageFileContentParser implements FileContentParser {

    @Override
    public boolean supports(FileParsingContext context) {
        return context.isImage() || FileTypeSupport.isImageFile(context.lowerFilename());
    }

    @Override
    public String parse(FileParsingContext context) throws Exception {
        return context.imagePlaceholder();
    }
}
