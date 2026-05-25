package com.ai.service.connector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DataConnectorSqlPolicyTest {

    private final DataConnectorSqlPolicy policy = new DataConnectorSqlPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM users",
            "select count(*) from orders",
            "WITH cte AS (SELECT 1) SELECT * FROM cte",
            "SHOW TABLES",
            "DESCRIBE users",
            "EXPLAIN SELECT * FROM orders"
    })
    void allowsReadOnlyStatements(String sql) {
        assertDoesNotThrow(() -> policy.validateReadOnlySql(sql));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DROP TABLE users",
            "DELETE FROM users",
            "TRUNCATE TABLE users",
            "UPDATE users SET name='x'",
            "INSERT INTO users VALUES(1)",
            "ALTER TABLE users ADD col INT",
            "CREATE TABLE evil(id INT)",
            "GRANT ALL ON *.* TO root"
    })
    void rejectsWriteStatements(String sql) {
        assertThrows(IllegalArgumentException.class, () -> policy.validateReadOnlySql(sql));
    }

    @Test
    void rejectsMultiStatements() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateReadOnlySql("SELECT 1; DROP TABLE users"));
    }

    @Test
    void rejectsCommentHiddenWrite() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateReadOnlySql("SELECT /* */ 1; DROP TABLE users"));
    }

    @Test
    void rejectsWriteHiddenInBlockComment() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateReadOnlySql("/* SELECT 1 */ DROP TABLE users"));
    }

    @Test
    void rejectsWriteAfterLineComment() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateReadOnlySql("-- safe\nDROP TABLE users"));
    }

    @Test
    void rejectsNullSql() {
        assertThrows(IllegalArgumentException.class, () -> policy.validateReadOnlySql(null));
    }

    @Test
    void rejectsBlankSql() {
        assertThrows(IllegalArgumentException.class, () -> policy.validateReadOnlySql("   "));
    }

    @Test
    void rejectsSelectIntoOutfile() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateReadOnlySql("SELECT * INTO OUTFILE '/tmp/x' FROM users"));
    }

    @Test
    void rejectsExecStatement() {
        assertThrows(IllegalArgumentException.class,
                () -> policy.validateReadOnlySql("EXEC sp_help"));
    }
}
