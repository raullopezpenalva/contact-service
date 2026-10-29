package com.raullopezpenalva.contact_service;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.application.AdminAccountService;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.persistence.AdminAccountRepository;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.operator.LegacySchemaBaseline;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.emailSender.EmailClient;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram.TelegramClient;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.system.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@Tag("postgres")
@SpringBootTest(properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate","app.notification.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AdminIdentityPostgresIT {
    @Container static final PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("identity").withUsername("identity").withPassword("isolated-test-password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",pg::getJdbcUrl);
        properties.add("spring.datasource.username",pg::getUsername);
        properties.add("spring.datasource.password",pg::getPassword);
        properties.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        properties.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired AdminAccountService accounts;
    @Autowired AdminAccountRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @MockitoBean EmailClient email;
    @MockitoBean TelegramClient telegram;
    static final String PASSWORD="Synthetic isolated password 12345";
    @BeforeEach void clean() {jdbc.update("DELETE FROM admin_sessions");repository.deleteAll();}

    @Test void canonicalProvisionIsRepeatSafeAndCredentialsRemainPrivate(CapturedOutput output) throws Exception {
        var first=accounts.provision("  OWNER  ",PASSWORD.toCharArray());
        var again=accounts.provision("owner","A different synthetic password".toCharArray());
        assertEquals(first.id(),again.id());assertEquals(1,repository.count());
        var stored=repository.findByLogin("owner").orElseThrow();
        assertTrue(stored.getPasswordHash().startsWith("$argon2id$"));
        assertNotEquals(PASSWORD,stored.getPasswordHash());
        assertTrue(accounts.verifyEligibleCredentials("OWNER",PASSWORD.toCharArray()));
        assertFalse(accounts.verifyEligibleCredentials("owner","wrong synthetic password".toCharArray()));
        assertFalse(accounts.verifyEligibleCredentials("unknown",PASSWORD.toCharArray()));
        assertFalse(json.writeValueAsString(first).contains("password"));
        assertFalse(json.writeValueAsString(stored).contains("password"));
        assertFalse(output.getAll().contains(PASSWORD));assertFalse(output.getAll().contains(stored.getPasswordHash()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/contact/messages")
            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("owner",PASSWORD)))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test void postgresEnforcesCanonicalUniquenessAndRoleBoundary() {
        var owner=accounts.provision("owner",PASSWORD.toCharArray());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update(
            "INSERT INTO admin_account_roles(account_id,role) VALUES (?,?)",owner.id(),"UNAPPROVED_ROLE"));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update(
            "UPDATE admin_accounts SET login='OWNER' WHERE id=?",owner.id()));
        var second=accounts.provision("second",PASSWORD.toCharArray());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update(
            "UPDATE admin_accounts SET login='owner' WHERE id=?",second.id()));
    }

    @Test void resetAndDisableInvalidateCredentialsAndExistingSessionRows() {
        var owner=accounts.provision("owner",PASSWORD.toCharArray());
        accounts.provision("backup",PASSWORD.toCharArray());
        jdbc.update("INSERT INTO admin_sessions(id,account_id,authentication_version,created_at,expires_at) VALUES (?,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+interval '10 minutes')",UUID.randomUUID(),owner.id());
        var changed=accounts.resetPassword("owner","A newly rotated synthetic password".toCharArray());
        assertEquals(1,changed.authenticationVersion());
        assertFalse(accounts.verifyEligibleCredentials("owner",PASSWORD.toCharArray()));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM admin_sessions WHERE revoked_at IS NOT NULL",Integer.class));
        accounts.setEnabled("owner",false);
        assertFalse(accounts.verifyEligibleCredentials("owner","A newly rotated synthetic password".toCharArray()));
        accounts.setEnabled("owner",true);
        assertTrue(accounts.verifyEligibleCredentials("owner","A newly rotated synthetic password".toCharArray()));
    }

    @Test void concurrentDisablesCannotRemoveTheLastAdministrator() throws Exception {
        accounts.provision("first",PASSWORD.toCharArray());accounts.provision("second",PASSWORD.toCharArray());
        var start=new CountDownLatch(1);var changed=new AtomicInteger();var rejected=new AtomicInteger();
        try(var pool=Executors.newFixedThreadPool(2)) {
            List<Future<?>> tasks=new ArrayList<>();
            for(String login:List.of("first","second")) tasks.add(pool.submit(()->{
                try {start.await();accounts.setEnabled(login,false);changed.incrementAndGet();}
                catch(IllegalStateException ex) {rejected.incrementAndGet();}
                catch(InterruptedException ex) {Thread.currentThread().interrupt();throw new RuntimeException(ex);}
            }));
            start.countDown();for(var task:tasks)task.get(20,TimeUnit.SECONDS);
        }
        assertEquals(1,changed.get());assertEquals(1,rejected.get());assertEquals(1,repository.countEnabledAdmins());
    }

    @Test void passwordAndSessionRevocationRollBackTogetherOnDatabaseFailure(CapturedOutput output) {
        var owner=accounts.provision("owner",PASSWORD.toCharArray());
        String hash=repository.findById(owner.id()).orElseThrow().getPasswordHash();
        jdbc.update("INSERT INTO admin_sessions(id,account_id,authentication_version,created_at,expires_at) VALUES (?,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+interval '10 minutes')",UUID.randomUUID(),owner.id());
        jdbc.execute("ALTER TABLE admin_accounts ADD CONSTRAINT test_version_guard CHECK (authentication_version=0)");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->
                accounts.resetPassword("owner","A new synthetic rollback password".toCharArray()));
        } finally {jdbc.execute("ALTER TABLE admin_accounts DROP CONSTRAINT test_version_guard");}
        assertEquals(hash,repository.findById(owner.id()).orElseThrow().getPasswordHash());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM admin_sessions WHERE revoked_at IS NOT NULL",Integer.class));
        assertFalse(output.getAll().contains(hash));
        assertFalse(output.getAll().contains("$argon2id$"));
    }

    private DataSource separateDatabase() throws SQLException {
        String name="migration_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());var s=c.createStatement()) {s.execute("CREATE DATABASE "+name);}
        return new DriverManagerDataSource("jdbc:postgresql://"+pg.getHost()+":"+pg.getMappedPort(5432)+"/"+name,pg.getUsername(),pg.getPassword());
    }
    private void legacy(DataSource source) throws SQLException {
        try(var c=source.getConnection()) {ScriptUtils.executeSqlScript(c,new ClassPathResource("db/legacy-v1_3_0.sql"));}
    }
    @Test void legacyUpgradeRequiresGuardedBaselineAndPreservesRows() throws Exception {
        var source=separateDatabase();legacy(source);var db=new JdbcTemplate(source);
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO contact_messages(id,content,created_at,email,notification_attempts,status,subject,updated_at) VALUES (?,'Synthetic legacy content',CURRENT_TIMESTAMP,'legacy@example.com',0,'NEW','Legacy row',CURRENT_TIMESTAMP)",id);
        var flyway=Flyway.configure().dataSource(source).baselineOnMigrate(false).load();
        assertThrows(org.flywaydb.core.api.FlywayException.class,flyway::migrate);
        LegacySchemaBaseline.baseline(source);flyway.migrate();
        assertEquals(id,db.queryForObject("SELECT id FROM contact_messages",UUID.class));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM admin_accounts",Integer.class));
        assertEquals("2",flyway.info().current().getVersion().getVersion());
    }
    @Test void legacyDriftAbortsWithoutBaselineOrPersistentScratchSchema() throws Exception {
        var source=separateDatabase();legacy(source);var db=new JdbcTemplate(source);
        db.execute("ALTER TABLE contact_messages ADD COLUMN unexpected text");
        assertThrows(IllegalStateException.class,()->LegacySchemaBaseline.baseline(source));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_name='flyway_schema_history'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname LIKE 'contact_baseline_%'",Integer.class));
    }
    @Test void changedCheckLiteralsAreNotMistakenForFormatting() throws Exception {
        var source=separateDatabase();legacy(source);var db=new JdbcTemplate(source);
        db.execute("ALTER TABLE contact_messages DROP CONSTRAINT contact_messages_status_check");
        db.execute("ALTER TABLE contact_messages ADD CONSTRAINT contact_messages_status_check CHECK (status IN ('N(EW','READ','ARCHIVED','SPAM','NOTIFICATION_FAILED'))");
        assertThrows(IllegalStateException.class,()->LegacySchemaBaseline.baseline(source));
    }
}
