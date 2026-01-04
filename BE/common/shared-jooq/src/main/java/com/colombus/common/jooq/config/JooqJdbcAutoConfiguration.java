package com.colombus.common.jooq.config;

import com.colombus.common.utility.json.Jsons;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

@AutoConfiguration
@ConditionalOnClass({DSLContext.class, DataSource.class})
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
public class JooqJdbcAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(DSLContext.class)
    public DSLContext jdbcDslContext(DataSource dataSource) {
        DefaultConfiguration config = new DefaultConfiguration();
        config.set(SQLDialect.POSTGRES);
        config.setDataSource(new TransactionAwareDataSourceProxy(dataSource));
        config.set(new Settings()
            .withExecuteWithOptimisticLocking(true)
            .withMapRecordComponentParameterNames(true)
        );
        config.data("objectMapper", Jsons.getMapper());
        return DSL.using(config);
    }

    @Bean
    @ConditionalOnMissingBean(DefaultConfigurationCustomizer.class)
    public DefaultConfigurationCustomizer jooqConfigurationCustomizer() {
        return config -> {
            // 여기에 추가적인 공통 jOOQ 설정을 할 수 있습니다.
            // 예: 커스텀 ExceptionTranslator 등록
            // config.setExceptionTranslator(new MyCustomExceptionTranslator());
        };
    }
}
