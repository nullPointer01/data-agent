package com.ai.service.file;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 将 PDF 文件解析为文本。
 *
 * @author data-agent
 */
@Component
@Order(10)
public class PdfFileContentParser implements FileContentParser {

    @Override
    public boolean supports(FileParsingContext context) {
        return context.lowerFilename().endsWith(FileTypeSupport.FILE_EXTENSION_PDF);
    }

    @Override
    public String parse(FileParsingContext context) throws Exception {
        try (InputStream inputStream = context.openInputStream();
                PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}
