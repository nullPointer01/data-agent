package com.ai.persistence;

import com.ai.util.CryptoUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/** 仅由敏感字段的 XML 显式引用；保持原 AES-GCM 密文兼容，不加密普通字符串列。 */
public final class EncryptedStringTypeHandler extends BaseTypeHandler<String> {
    private final CryptoUtil crypto;

    public EncryptedStringTypeHandler(CryptoUtil crypto) { this.crypto = crypto; }

    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, String value, JdbcType jdbcType)
            throws SQLException {
        statement.setString(index, crypto.encrypt(value));
    }

    @Override
    public String getNullableResult(ResultSet result, String column) throws SQLException {
        return crypto.decrypt(result.getString(column));
    }

    @Override
    public String getNullableResult(ResultSet result, int column) throws SQLException {
        return crypto.decrypt(result.getString(column));
    }

    @Override
    public String getNullableResult(CallableStatement statement, int column) throws SQLException {
        return crypto.decrypt(statement.getString(column));
    }
}
