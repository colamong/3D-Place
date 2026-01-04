package com.colombus.common.jooq.config;

import io.r2dbc.spi.ConnectionFactory;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass({DSLContext.class, ConnectionFactory.class})
@AutoConfigureAfter(R2dbcAutoConfiguration.class)
public class JooqR2dbcAutoConfiguration {

    @Bean
    @ConditionalOnBean(ConnectionFactory.class)
    @ConditionalOnMissingBean(DSLContext.class)
    public DSLContext r2dbcDslContext(ConnectionFactory connectionFactory) {
        Settings settings = new Settings().withExecuteWithOptimisticLocking(true);
        return DSL.using(connectionFactory, SQLDialect.POSTGRES, settings);
    }
}
