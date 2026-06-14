package com.ai.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 加密字符串列的 JPA 转换器。
 *
 * @author data-agent
 */
@Converter(autoApply = false)
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static CryptoUtil cryptoUtil;

    public static void setCryptoUtil(CryptoUtil util) {
        EncryptedStringConverter.cryptoUtil = util;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (cryptoUtil == null || attribute == null) {
            return attribute;
        }
        return cryptoUtil.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (cryptoUtil == null || dbData == null) {
            return dbData;
        }
        return cryptoUtil.decrypt(dbData);
    }
}
