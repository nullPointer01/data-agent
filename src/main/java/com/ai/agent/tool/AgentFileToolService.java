package com.ai.agent.tool;

import com.ai.file.dto.FileListResponse;
import com.ai.file.dto.FileResponse;
import com.ai.service.file.FileProcessingService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 使用的文件类工具。
 *
 * @author data-agent
 */
@Service
public class AgentFileToolService {

    private static final int FILE_CONTENT_PREVIEW_LENGTH = 3000;
    private static final int NUMBER_SAMPLE_ROWS_WITH_HEADER = 6;
    private static final String EMPTY_FILE_MESSAGE = "没有已上传的文件，请先上传文件";
    private static final String EMPTY_FILE_CONTENT_MESSAGE = "文件不存在或内容为空";
    private static final String EMPTY_FILE_BODY_MESSAGE = "文件内容为空";
    private static final String DEFAULT_DELIMITER = "\t";
    private static final String COMMA_DELIMITER = ",";
    private static final String SEMICOLON_DELIMITER = ";";
    private static final String UNKNOWN_VALUE = "-";

    private final FileProcessingService fileProcessingService;

    public AgentFileToolService(FileProcessingService fileProcessingService) {
        this.fileProcessingService = fileProcessingService;
    }

    public String getFileContent(String fileId) {
        String content = fileProcessingService.getFileContent(fileId);
        if (content == null) {
            return EMPTY_FILE_CONTENT_MESSAGE;
        }
        if (content.length() > FILE_CONTENT_PREVIEW_LENGTH) {
            return content.substring(0, FILE_CONTENT_PREVIEW_LENGTH)
                    + "\n...[文件已截断，共" + content.length() + "字符]";
        }
        return content;
    }

    public String listFiles() {
        FileListResponse response = fileProcessingService.listFiles();
        List<FileResponse> files = response.files();
        if (files == null || files.isEmpty()) {
            return EMPTY_FILE_MESSAGE;
        }
        return files.stream()
                .map(this::formatFile)
                .collect(Collectors.joining("\n"));
    }

    public String analyzeFileData(String fileId) {
        String content = fileProcessingService.getFileContent(fileId);
        if (content == null || content.isEmpty()) {
            return EMPTY_FILE_CONTENT_MESSAGE;
        }

        String[] lines = content.split("\n");
        if (lines.length == 0) {
            return EMPTY_FILE_BODY_MESSAGE;
        }

        String delimiter = detectDelimiter(lines[0]);
        String[] headers = splitLine(lines[0], delimiter);
        StringBuilder resultBuilder = new StringBuilder();
        appendDataOverview(resultBuilder, lines.length, headers);
        appendNumericStats(resultBuilder, lines, headers, delimiter);
        return resultBuilder.toString();
    }

    private void appendDataOverview(StringBuilder resultBuilder, int rowCount, String[] headers) {
        resultBuilder.append("## 数据概览\n");
        resultBuilder.append("- 总行数: ").append(rowCount).append("\n");
        resultBuilder.append("- 列数: ").append(headers.length).append("\n");
        resultBuilder.append("- 列名: ").append(String.join(", ", headers)).append("\n\n");
    }

    private void appendNumericStats(StringBuilder resultBuilder, String[] lines, String[] headers, String delimiter) {
        if (lines.length <= 1) {
            return;
        }
        resultBuilder.append("## 数值列统计\n");
        for (int columnIndex = 0; columnIndex < headers.length; columnIndex++) {
            NumericSummary summary = summarizeNumericColumn(lines, delimiter, columnIndex);
            if (summary.count() > 0) {
                resultBuilder.append(String.format(
                        "**%s**: 有效数据%d条, 求和=%.2f, 均值=%.2f, 最小=%.2f, 最大=%.2f\n",
                        headers[columnIndex],
                        summary.count(),
                        summary.sum(),
                        summary.average(),
                        summary.min(),
                        summary.max()));
            }
        }
        appendSampleRows(resultBuilder, lines);
    }

    private NumericSummary summarizeNumericColumn(String[] lines, String delimiter, int columnIndex) {
        double sum = 0D;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        int numericCount = 0;

        for (int rowIndex = 1; rowIndex < lines.length; rowIndex++) {
            if (lines[rowIndex].trim().isEmpty()) {
                continue;
            }
            String[] cells = splitLine(lines[rowIndex], delimiter);
            if (columnIndex >= cells.length) {
                continue;
            }
            try {
                double value = Double.parseDouble(cells[columnIndex].trim());
                sum += value;
                min = Math.min(min, value);
                max = Math.max(max, value);
                numericCount++;
            } catch (NumberFormatException ignored) {
                // 构建数值摘要时忽略非数值单元格。
            }
        }
        return new NumericSummary(sum, min, max, numericCount);
    }

    private void appendSampleRows(StringBuilder resultBuilder, String[] lines) {
        resultBuilder.append("\n## 前5行样例\n");
        int sampleRows = Math.min(NUMBER_SAMPLE_ROWS_WITH_HEADER, lines.length);
        for (int rowIndex = 0; rowIndex < sampleRows; rowIndex++) {
            resultBuilder.append(lines[rowIndex]).append("\n");
        }
    }

    private String formatFile(FileResponse file) {
        return "- " + defaultValue(file.fileId())
                + " | " + defaultValue(file.filename())
                + " | " + defaultValue(file.contentType());
    }

    private String detectDelimiter(String line) {
        int tabCount = line.split("\t", -1).length - 1;
        int commaCount = line.split(COMMA_DELIMITER, -1).length - 1;
        int semicolonCount = line.split(SEMICOLON_DELIMITER, -1).length - 1;
        if (tabCount >= commaCount && tabCount >= semicolonCount && tabCount > 0) {
            return DEFAULT_DELIMITER;
        }
        if (commaCount >= tabCount && commaCount >= semicolonCount && commaCount > 0) {
            return COMMA_DELIMITER;
        }
        if (semicolonCount > 0) {
            return SEMICOLON_DELIMITER;
        }
        return DEFAULT_DELIMITER;
    }

    private String[] splitLine(String line, String delimiter) {
        if (COMMA_DELIMITER.equals(delimiter)) {
            String[] raw = line.split(COMMA_DELIMITER, -1);
            for (int index = 0; index < raw.length; index++) {
                raw[index] = raw[index].trim().replaceAll("^\"|\"$", "");
            }
            return raw;
        }
        return line.split(delimiter, -1);
    }

    private String defaultValue(Object value) {
        return value == null ? UNKNOWN_VALUE : String.valueOf(value);
    }

    private record NumericSummary(double sum, double min, double max, int count) {

        double average() {
            return count == 0 ? 0D : sum / count;
        }
    }
}
