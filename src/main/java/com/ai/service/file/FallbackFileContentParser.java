package com.ai.service.file;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 不支持文件类型的显式拒绝解析器。
 *
 * <p>该解析器必须保持最低优先级，避免把图片或其他二进制文件按 UTF-8 文本读取并伪装成解析成功。</p>
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
    public String parse(FileParsingContext context) {
        throw new IllegalArgumentException("不支持的文件类型: " + context.filename());
    }
}
