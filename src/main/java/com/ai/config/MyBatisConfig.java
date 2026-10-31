package com.ai.config;

import com.ai.persistence.EncryptedStringTypeHandler;
import com.ai.util.CryptoUtil;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.type.StringTypeHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.autoconfigure.SqlSessionFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** MyBatis 只扫描显式 Mapper；事务由 Spring 的同一主库 DataSourceTransactionManager 管理。 */
@Configuration
@MapperScan(basePackages = "com.ai", annotationClass = Mapper.class)
public class MyBatisConfig {
    @Bean
    public SqlSessionFactoryBeanCustomizer encryptedColumnHandler(CryptoUtil crypto) {
        // 加密处理器保留到显式类型索引；最后恢复普通 String 默认处理，避免误加密所有文本。
        return factory -> factory.setTypeHandlers(new EncryptedStringTypeHandler(crypto), new StringTypeHandler());
    }
}
