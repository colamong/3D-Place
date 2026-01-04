package com.colombus.snapshot.config;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.boot.autoconfigure.jooq.DefaultConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class JooqConfig {

    @Bean
    public DefaultConfigurationCustomizer jooqConfigurationCustomizer() {
        return (DefaultConfiguration configuration) -> {
            Settings settings = new Settings()
                    .withRenderNameCase(RenderNameCase.LOWER);
            configuration.set(settings);
            configuration.set(SQLDialect.POSTGRES);
        };
    }

    @Bean
    public DSLContext dsl(DataSource ds) {
        return DSL.using(ds, SQLDialect.POSTGRES,
                new Settings().withExecuteWithOptimisticLocking(true));
    }
}