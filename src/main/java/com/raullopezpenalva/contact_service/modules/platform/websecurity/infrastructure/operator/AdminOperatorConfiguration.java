package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.operator;

import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.application.*;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.domain.AdminAccount;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.persistence.AdminAccountRepository;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.persistence.DatabaseMigrationPolicy;

@Configuration(proxyBeanMethods=false)
@Profile("admin-operator-internal")
@ImportAutoConfiguration({DataSourceAutoConfiguration.class,JdbcTemplateAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class,TransactionAutoConfiguration.class,FlywayAutoConfiguration.class})
@EntityScan(basePackageClasses=AdminAccount.class)
@EnableJpaRepositories(basePackageClasses=AdminAccountRepository.class)
@Import({AdminAccountService.class,AdminPasswordHasher.class,DatabaseMigrationPolicy.class})
public class AdminOperatorConfiguration {}
