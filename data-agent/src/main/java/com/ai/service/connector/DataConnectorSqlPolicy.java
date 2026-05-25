package com.ai.service.connector;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 只读 SQL 安全策略，执行多层校验防止写操作和注入。
 *
 * @author data-agent
 */
@Component
public class DataConnectorSqlPolicy {

    private static final String TYPE_HTTP = "http";
    private static final String TYPE_JSON = "json";
    private static final String TYPE_CSV = "csv";
    private static final String TYPE_TEXT = "text";
    private static final String TYPE_MYSQL = "mysql";
    private static final String READ_ONLY_SQL_PREFIX_SELECT = "SELECT";
    private static final String READ_ONLY_SQL_PREFIX_WITH = "WITH";
    private static final String READ_ONLY_SQL_PREFIX_SHOW = "SHOW";
    private static final String READ_ONLY_SQL_PREFIX_DESCRIBE = "DESCRIBE";
    private static final String READ_ONLY_SQL_PREFIX_EXPLAIN = "EXPLAIN";
    private static final Pattern FORBIDDEN_KEYWORD_PATTERN = Pattern.compile(
            "\\b(DROP|DELETE|TRUNCATE|UPDATE|INSERT|ALTER|CREATE|MERGE|GRANT|REVOKE|"
                    + "EXEC|EXECUTE|CALL|INTO\\s+OUTFILE|INTO\\s+DUMPFILE|LOAD\\s+DATA|"
                    + "SET\\s|LOCK\\s|UNLOCK\\s|RENAME\\s|REPLACE\\s)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTI_STATEMENT_PATTERN = Pattern.compile(";\\s*\\S");
    private static final Pattern BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT_PATTERN = Pattern.compile("--[^\n]*");

    /**
     * 校验 SQL 仅为只读查询。
     *
     * @param sql SQL 语句
     */
    public void validateReadOnlySql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        if (MULTI_STATEMENT_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("安全限制：不允许执行多条 SQL 语句");
        }
        String stripped = stripComments(sql).trim();
        if (stripped.isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }
        String upper = stripped.toUpperCase(Locale.ROOT);
        if (!upper.startsWith(READ_ONLY_SQL_PREFIX_SELECT)
                && !upper.startsWith(READ_ONLY_SQL_PREFIX_WITH)
                && !upper.startsWith(READ_ONLY_SQL_PREFIX_SHOW)
                && !upper.startsWith(READ_ONLY_SQL_PREFIX_DESCRIBE)
                && !upper.startsWith(READ_ONLY_SQL_PREFIX_EXPLAIN)) {
            throw new IllegalArgumentException("安全限制：只允许执行只读查询语句");
        }
        if (FORBIDDEN_KEYWORD_PATTERN.matcher(stripped).find()) {
            throw new IllegalArgumentException("安全限制：SQL 中包含不允许的写操作关键词");
        }
    }

    private String stripComments(String sql) {
        String result = BLOCK_COMMENT_PATTERN.matcher(sql).replaceAll(" ");
        return LINE_COMMENT_PATTERN.matcher(result).replaceAll(" ");
    }

    /**
     * Normalizes datasource type strings.
     *
     * @param type datasource type
     * @return normalized type
     */
    public String normalizeType(String type) {
        return type == null ? TYPE_MYSQL : type.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Checks whether the datasource should be handled as HTTP-like content.
     *
     * @param type datasource type
     * @return true when HTTP/JSON/CSV/TEXT
     */
    public boolean isHttpType(String type) {
        return switch (normalizeType(type)) {
            case TYPE_HTTP, TYPE_JSON, TYPE_CSV, TYPE_TEXT -> true;
            default -> false;
        };
    }

    /**
     * Checks whether an HTTP status code is successful.
     *
     * @param statusCode HTTP status code
     * @return true for 2xx responses
     */
    public boolean isSuccessfulHttp(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * Detects JSON-like text payloads.
     *
     * @param body response body
     * @return true when the payload starts with JSON delimiters
     */
    public boolean looksLikeJson(String body) {
        if (body == null) {
            return false;
        }
        String trimmed = body.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    /**
     * Clamps requested row limits to a safe range.
     *
     * @param limit requested limit
     * @param maxLimit upper bound
     * @return bounded limit
     */
    public int normalizeLimit(int limit, int maxLimit) {
        return Math.max(1, Math.min(limit, maxLimit));
    }

    /**
     * Truncates text to the requested maximum length.
     *
     * @param body text body
     * @param maxLength max length
     * @return truncated text
     */
    public String truncate(String body, int maxLength) {
        if (body == null) {
            return "";
        }
        return body.substring(0, Math.min(maxLength, body.length()));
    }
}
