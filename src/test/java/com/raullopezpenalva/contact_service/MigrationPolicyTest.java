package com.raullopezpenalva.contact_service;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.persistence.DatabaseMigrationPolicy;
import static org.junit.jupiter.api.Assertions.*;

class MigrationPolicyTest {
    @ParameterizedTest @ValueSource(strings={"baseline","clean","validation"})
    void unsafeFlywayOverridesFailBeforeAnyDatabaseConnection(String option) {
        var configuration=Flyway.configure().dataSource("jdbc:postgresql://unreachable.invalid/unused","synthetic","synthetic");
        switch(option) {
            case "baseline" -> configuration.baselineOnMigrate(true);
            case "clean" -> configuration.cleanDisabled(false);
            case "validation" -> configuration.validateOnMigrate(false);
        }
        var strategy=new DatabaseMigrationPolicy().safeMigrations();
        assertThrows(IllegalStateException.class,()->strategy.migrate(configuration.load()));
    }
    @Test void productionCannotDisableMigrationsOrReenableHibernateUpdates() {
        assertThrows(IllegalStateException.class,()->verify(new MockEnvironment()
            .withProperty("spring.flyway.enabled","false")));
        assertThrows(IllegalStateException.class,()->verify(new MockEnvironment()
            .withProperty("spring.jpa.hibernate.ddl-auto","update")));
        var isolated=new MockEnvironment().withProperty("spring.flyway.enabled","false")
            .withProperty("spring.datasource.driver-class-name","org.h2.Driver").withProperty("spring.jpa.hibernate.ddl-auto","create-drop");
        isolated.setActiveProfiles("test");
        assertDoesNotThrow(()->verify(isolated));
    }

    private void verify(MockEnvironment environment) {
        DatabaseMigrationPolicy.migrationSettingsGuard(environment)
            .postProcessBeanFactory(new org.springframework.beans.factory.support.DefaultListableBeanFactory());
    }

    @Test void unsafeStartupFailsBeforeDatasourceCanBeCreated() {
        var created=new java.util.concurrent.atomic.AtomicBoolean();
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
            .withUserConfiguration(DatabaseMigrationPolicy.class)
            .withPropertyValues("spring.flyway.enabled=false","spring.jpa.hibernate.ddl-auto=update")
            .withBean(javax.sql.DataSource.class,()->{
                created.set(true);return new org.springframework.jdbc.datasource.DriverManagerDataSource();
            }).run(context->{assertNotNull(context.getStartupFailure());assertFalse(created.get());});
    }
}
