package com.raullopezpenalva.contact_service;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.application.*;

/** Real signing keys and persisted accounts/sessions; never mocks JWT authentication. */
public abstract class JwtTestSupport {
    static final String PASSWORD="Isolated fixture password 47!";
    static final Path KEYS=keys();
    private static Path keys() {
        try {
            Path dir=Files.createTempDirectory("contact-jwt-tests-");
            var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(3072);
            var pair=generator.generateKeyPair();
            Files.createDirectory(dir.resolve("public"));
            pem(dir.resolve("private.pem"),"PRIVATE KEY",pair.getPrivate().getEncoded());
            Files.setPosixFilePermissions(dir.resolve("private.pem"),PosixFilePermissions.fromString("rw-------"));
            pem(dir.resolve("public/test.pub.pem"),"PUBLIC KEY",pair.getPublic().getEncoded());
            return dir;
        } catch(Exception ex){throw new IllegalStateException("Cannot create isolated test signing material");}
    }
    private static void pem(Path file,String type,byte[] data) throws Exception {
        Files.writeString(file,"-----BEGIN "+type+"-----\n"+Base64.getMimeEncoder(64,new byte[]{'\n'}).encodeToString(data)+"\n-----END "+type+"-----\n");
    }
    @DynamicPropertySource static void jwtProperties(DynamicPropertyRegistry props) {
        props.add("app.security.jwt.issuer",()->"https://admin.example.test");
        props.add("app.security.jwt.active-key-id",()->"test");
        props.add("app.security.jwt.private-key-file",()->KEYS.resolve("private.pem").toString());
        props.add("app.security.jwt.public-key-directory",()->KEYS.resolve("public").toString());
    }
    @Autowired protected AdminAccountService adminAccounts;
    @Autowired protected AdminLoginService adminLogin;
    @Autowired protected JdbcTemplate jwtJdbc;
    private String fixtureToken;
    @BeforeEach void resetJwtFixture() throws Exception {
        fixtureToken=null;
        try(var connection=jwtJdbc.getDataSource().getConnection()) {
        if(connection.getMetaData().getDatabaseProductName().equals("H2")) {
            jwtJdbc.execute("CREATE TABLE IF NOT EXISTS admin_management_lock(id integer PRIMARY KEY)");
            jwtJdbc.execute("MERGE INTO admin_management_lock KEY(id) VALUES(1)");
        }
        }
        jwtJdbc.update("DELETE FROM admin_sessions WHERE account_id IN (SELECT id FROM admin_accounts WHERE login='fixture-admin')");
    }
    protected String bearer() {
        if(fixtureToken==null) {
            adminAccounts.provision("fixture-admin",PASSWORD.toCharArray());
            fixtureToken=adminLogin.login("fixture-admin",PASSWORD.toCharArray());
        }
        return fixtureToken;
    }
    protected RequestPostProcessor adminBearer(){return request->{request.addHeader("Authorization","Bearer "+bearer());return request;};}
}
