package com.ai.service.file;

import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/** 通用正文提取交给官方 DocumentParser；上传状态和身份由文件服务维护。 */
@Component
@Order(10)
public class FrameworkDocumentFileContentParser implements FileContentParser {
    @Override
    public boolean supports(FileParsingContext context) {
        String name = context.lowerFilename();
        return name.endsWith(FileTypeSupport.FILE_EXTENSION_PDF)
                || name.endsWith(FileTypeSupport.FILE_EXTENSION_DOCX)
                || name.endsWith(FileTypeSupport.FILE_EXTENSION_PPTX);
    }

    @Override
    public String parse(FileParsingContext context) throws Exception {
        DocumentParser parser = context.lowerFilename().endsWith(FileTypeSupport.FILE_EXTENSION_PDF)
                ? new ApachePdfBoxDocumentParser() : new ApachePoiDocumentParser();
        try (InputStream input = context.openInputStream()) {
            return parser.parse(input).text();
        } catch (BlankDocumentException blank) {
            // 空文件/无文字的 PDF 保持原文件生命周期语义：完成解析但不进入检索索引。
            return "";
        }
    }
}
