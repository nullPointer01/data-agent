package com.ai.service.file;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 将图像上传解析为轻量级占位符，而不是原始二进制文本。
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
