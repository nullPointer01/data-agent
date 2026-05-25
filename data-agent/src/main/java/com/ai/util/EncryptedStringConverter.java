package com.ai.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA converter for encrypted string columns.
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
