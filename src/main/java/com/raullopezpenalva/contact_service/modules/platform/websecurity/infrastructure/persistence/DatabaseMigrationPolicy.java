package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.persistence;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;

@Configuration(proxyBeanMethods=false)
public class DatabaseMigrationPolicy {
    @Bean
    public static BeanFactoryPostProcessor migrationSettingsGuard(Environment environment) {
        // Run before singleton creation: Hibernate must not touch schema first.
        return factory -> verifySettings(environment);
    }

    private static void verifySettings(Environment environment) {
        boolean isolatedH2=environment.acceptsProfiles(Profiles.of("test"))
            && "org.h2.Driver".equals(environment.getProperty("spring.datasource.driver-class-name"));
        if(!isolatedH2 && (!environment.getProperty("spring.flyway.enabled",Boolean.class,true)
                || !"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto","validate"))))
            throw new IllegalStateException("PostgreSQL requires versioned migrations and validate-only ORM schema handling");
    }
    @Bean
    public FlywayMigrationStrategy safeMigrations() {
        return flyway -> {
            var configuration=flyway.getConfiguration();
            if(configuration.isBaselineOnMigrate() || !configuration.isCleanDisabled() || !configuration.isValidateOnMigrate())
                throw new IllegalStateException("Unsafe migration settings; automatic baseline/clean or disabled validation are refused");
            flyway.migrate();
        };
    }
}
