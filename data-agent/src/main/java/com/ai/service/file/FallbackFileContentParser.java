package com.ai.service.file;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 没有专门策略的文件的备用解析器。
 *
 * @author data-agent
 */
@Component
@Order(200)
public class FallbackFileContentParser implements FileContentParser {

    @Override
    public boolean supports(FileParsingContext context) {
        return true;
    }

    @Override
    public String parse(FileParsingContext context) throws Exception {
        return context.readText();
    }
}
