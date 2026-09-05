package com.ai.service.file;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 将 DOCX 文件解析为文本。
 *
 * @author data-agent
 */
@Component
@Order(30)
public class DocxFileContentParser implements FileContentParser {

    @Override
    public boolean supports(FileParsingContext context) {
        return context.lowerFilename().endsWith(FileTypeSupport.FILE_EXTENSION_DOCX);
    }

    @Override
    public String parse(FileParsingContext context) throws Exception {
        try (InputStream inputStream = context.openInputStream();
                XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder content = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                content.append(paragraph.getText()).append('\n');
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        content.append(cell.getText()).append('\t');
                    }
                    content.append('\n');
                }
            }
            return content.toString();
        }
    }
}
