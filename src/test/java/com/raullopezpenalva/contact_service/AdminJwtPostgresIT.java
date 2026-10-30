package com.raullopezpenalva.contact_service;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.application.*;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.persistence.*;
import org.springframework.security.oauth2.jwt.*;
import static org.junit.jupiter.api.Assertions.*;

/** Runs the HTTP lifecycle suite against actual PostgreSQL as well as H2. */
@Testcontainers
@Tag("postgres")
@org.springframework.boot.test.context.SpringBootTest(properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate","app.notification.enabled=false"})
class AdminJwtPostgresIT extends AdminJwtIT {
    @Container static final PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("jwt").withUsername("jwt").withPassword("isolated-jwt-test-password");
    @DynamicPropertySource static void database(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url",pg::getJdbcUrl);props.add("spring.datasource.username",pg::getUsername);
        props.add("spring.datasource.password",pg::getPassword);props.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        props.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
    }
    @Autowired AdminAccountRepository accountRepository;
    @Autowired AdminSessionRepository sessionRepository;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;
    @Autowired AdminPasswordHasher passwords;
    @Test void concurrentIssuanceEnforcesCapAndCleanupIsBounded() throws Exception {
        String name="concurrent-"+UUID.randomUUID();var account=adminAccounts.provision(name,PASSWORD.toCharArray());
        // Session insertion and cap are serialized with account-management mutations.
        try(var pool=Executors.newFixedThreadPool(12)) {
            var start=new CountDownLatch(1);List<Future<Boolean>> tasks=new ArrayList<>();
            for(int i=0;i<12;i++) tasks.add(pool.submit(()->{
                start.await();try {sessions.issue(account.id(),0);return true;}catch(AuthFailure limited){return false;}
            }));
            start.countDown();int accepted=0;for(var task:tasks)if(task.get(20,TimeUnit.SECONDS))accepted++;
            assertEquals(10,accepted);
        }
        adminAccounts.revokeSessions(name);
        for(int i=0;i<205;i++) jwtJdbc.update("INSERT INTO admin_sessions(id,account_id,authentication_version,created_at,expires_at) VALUES (?,?,0,CURRENT_TIMESTAMP-interval '20 minutes',CURRENT_TIMESTAMP-interval '10 minutes')",UUID.randomUUID(),account.id());
        sessions.issue(account.id(),1);
        assertEquals(105,jwtJdbc.queryForObject("SELECT count(*) FROM admin_sessions WHERE expires_at<CURRENT_TIMESTAMP",Integer.class));
    }
    @Test void validationSurvivesServiceRecreationAndUsesInjectableClock() {
        String name="restart-"+UUID.randomUUID();adminAccounts.provision(name,PASSWORD.toCharArray());
        String token=adminLogin.login(name,PASSWORD.toCharArray());Jwt jwt=decoder.decode(token);
        var recreated=new AdminSessionService(accountRepository,sessionRepository,jwtJdbc,Clock.systemUTC(),encoder,keys,passwords);
        assertFalse(recreated.effectiveRoles(jwt).isEmpty());
        var future=new AdminSessionService(accountRepository,sessionRepository,jwtJdbc,
            Clock.fixed(jwt.getExpiresAt().plusSeconds(1),ZoneOffset.UTC),encoder,keys,passwords);
        assertThrows(org.springframework.security.core.AuthenticationException.class,()->future.effectiveRoles(jwt));
        adminAccounts.revokeSessions(name);
        assertThrows(org.springframework.security.core.AuthenticationException.class,()->recreated.effectiveRoles(jwt));
    }
    @Test void localKeyOverlapRetirementAndCompromiseAreEnforced() throws Exception {
        String name="rotation-"+UUID.randomUUID();adminAccounts.provision(name,PASSWORD.toCharArray());
        String oldToken=adminLogin.login(name,PASSWORD.toCharArray());
        var directory=java.nio.file.Files.createTempDirectory("rotation-");var pub=java.nio.file.Files.createDirectory(directory.resolve("public"));
        java.nio.file.Files.copy(KEYS.resolve("public/test.pub.pem"),pub.resolve("test.pub.pem"));
        var next=new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(3072).keyID("next").generate();
        var encoder64=java.util.Base64.getMimeEncoder(64,new byte[]{'\n'});
        var privateFile=directory.resolve("private.pem");
        java.nio.file.Files.writeString(privateFile,"-----BEGIN PRIVATE KEY-----\n"+encoder64.encodeToString(next.toPrivateKey().getEncoded())+"\n-----END PRIVATE KEY-----\n");
        java.nio.file.Files.setPosixFilePermissions(privateFile,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
        java.nio.file.Files.writeString(pub.resolve("next.pub.pem"),"-----BEGIN PUBLIC KEY-----\n"+encoder64.encodeToString(next.toPublicKey().getEncoded())+"\n-----END PUBLIC KEY-----\n");
        var material=new com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.jwt.JwtKeyMaterial("https://admin.example.test","next",privateFile,pub);
        var configuration=new com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.jwt.AdminJwtConfiguration();
        var overlapDecoder=configuration.jwtDecoder(material,Clock.systemUTC());
        assertFalse(sessions.effectiveRoles(overlapDecoder.decode(oldToken)).isEmpty());
        // Recreate issuance with the next signer against the same persistent session store.
        var nextService=new AdminSessionService(accountRepository,sessionRepository,jwtJdbc,Clock.systemUTC(),configuration.jwtEncoder(material),material,passwords);
        // Calls must still go through a transaction when using the manually constructed test instance.
        var transaction=new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        String nextToken=transaction.execute(status->nextService.issue(accountRepository.findByLogin(name).orElseThrow().getId(),0));
        assertFalse(sessions.effectiveRoles(overlapDecoder.decode(nextToken)).isEmpty());
        adminAccounts.revokeKey("test");
        assertThrows(org.springframework.security.core.AuthenticationException.class,()->sessions.effectiveRoles(overlapDecoder.decode(oldToken)));
        assertFalse(sessions.effectiveRoles(overlapDecoder.decode(nextToken)).isEmpty());
        java.nio.file.Files.delete(pub.resolve("test.pub.pem"));
        var retired=configuration.jwtDecoder(new com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.jwt.JwtKeyMaterial("https://admin.example.test","next",privateFile,pub),Clock.systemUTC());
        assertThrows(JwtException.class,()->retired.decode(oldToken));assertNotNull(retired.decode(nextToken));
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Test void missingSessionStorageFailsClosedWithoutLeakingDatabaseDiagnostics() throws Exception {
        String name="dbfailure-"+UUID.randomUUID();adminAccounts.provision(name,PASSWORD.toCharArray());
        String token=adminLogin.login(name,PASSWORD.toCharArray());
        jwtJdbc.execute("ALTER TABLE admin_sessions RENAME TO unavailable_admin_sessions");
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(ADMIN).header("Authorization","Bearer "+token))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message").value("Authentication required"));
        } finally {jwtJdbc.execute("ALTER TABLE unavailable_admin_sessions RENAME TO admin_sessions");}
    }
    @Test void selfServicePasswordAndRevocationRollBackTogetherOnStorageFailure() throws Exception {
        String name="rollback-"+UUID.randomUUID();var account=adminAccounts.provision(name,PASSWORD.toCharArray());
        String token=adminLogin.login(name,PASSWORD.toCharArray());
        jwtJdbc.execute("ALTER TABLE admin_sessions ADD CONSTRAINT test_revocation_guard CHECK (account_id <> '"+account.id()+"'::uuid OR revoked_at IS NULL)");
        try {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/change-password")
                .header("Authorization","Bearer "+token).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword",PASSWORD,"newPassword","Synthetic rollback replacement 47!"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isServiceUnavailable());
        } finally {jwtJdbc.execute("ALTER TABLE admin_sessions DROP CONSTRAINT test_revocation_guard");}
        assertFalse(sessions.effectiveRoles(decoder.decode(token)).isEmpty());
        assertTrue(adminAccounts.verifyEligibleCredentials(name,PASSWORD.toCharArray()));
        assertEquals(0,accountRepository.findById(account.id()).orElseThrow().getAuthenticationVersion());
    }
}
