package com.ai.service.file;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * Parses Excel spreadsheets into tabular text.
 *
 * @author data-agent
 */
@Component
@Order(20)
public class SpreadsheetFileContentParser implements FileContentParser {

    @Override
    public boolean supports(FileParsingContext context) {
        String lowerName = context.lowerFilename();
        return lowerName.endsWith(FileTypeSupport.FILE_EXTENSION_XLSX)
                || lowerName.endsWith(FileTypeSupport.FILE_EXTENSION_XLS);
    }

    @Override
    public String parse(FileParsingContext context) throws Exception {
        try (InputStream inputStream = context.openInputStream();
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                content.append("Sheet: ").append(sheet.getSheetName()).append('\n');
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }
                    for (int c = 0; c < row.getLastCellNum(); c++) {
                        Cell cell = row.getCell(c);
                        content.append(cell != null ? getCellValue(cell) : "").append('\t');
                    }
                    content.append('\n');
                }
                content.append('\n');
            }
            return content.toString();
        }
    }

    /**
     * Converts one spreadsheet cell into a readable text value.
     *
     * @param cell spreadsheet cell
     * @return text value for the cell
     */
    private String getCellValue(Cell cell) {
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getDateCellValue().toString()
                    : String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
