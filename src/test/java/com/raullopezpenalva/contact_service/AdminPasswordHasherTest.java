package com.raullopezpenalva.contact_service;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.application.AdminPasswordHasher;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.operator.AdminOperator;
import static org.junit.jupiter.api.Assertions.*;

class AdminPasswordHasherTest {
    @TempDir Path directory;
    @Test void hashesAreSaltedAdaptiveAndDoNotTruncateUnicodePasswords() {
        var passwords=new AdminPasswordHasher();
        char[] secret="🔐".repeat(128).toCharArray();
        String first=passwords.encode(secret),second=passwords.encode(secret);
        assertNotEquals(first,second);assertTrue(first.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
        assertTrue(passwords.matches(secret,first));
        assertFalse(passwords.matches(("🔐".repeat(127)+"X").toCharArray(),first));
        assertFalse(passwords.eligiblePasswordMatches(secret,first,false));
        assertThrows(IllegalArgumentException.class,()->passwords.encode("x".repeat(129).toCharArray()));
        assertThrows(IllegalArgumentException.class,()->passwords.encode("short".toCharArray()));
        assertThrows(IllegalArgumentException.class,()->passwords.encode(("x".repeat(15)+"\uD800").toCharArray()));
        assertFalse(passwords.matches(secret,"{noop}not-a-managed-password"));
        assertFalse(passwords.matches(secret,"$argon2id$corrupt-fixture"));
    }
    @Test void operatorRejectsPlaintextArgumentsAndPublicOrSymlinkInputs() throws Exception {
        assertThrows(IllegalArgumentException.class,()->AdminOperator.parse(new String[]{"provision","--login","owner","--password","secret"}));
        assertThrows(IllegalArgumentException.class,()->AdminOperator.parse(new String[]{"provision","--login","owner","--role","ADMIN"}));
        var file=directory.resolve("password");Files.writeString(file,"  A synthetic password with spaces  \n");
        Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));
        assertEquals("  A synthetic password with spaces  ",new String(AdminOperator.readPassword(file)));
        Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(IllegalArgumentException.class,()->AdminOperator.readPassword(file));
        Files.setPosixFilePermissions(file,PosixFilePermissions.fromString("rw-------"));
        var link=directory.resolve("link");Files.createSymbolicLink(link,file);
        assertThrows(IllegalArgumentException.class,()->AdminOperator.readPassword(link));
        Files.writeString(file,"x".repeat(515));
        assertThrows(IllegalArgumentException.class,()->AdminOperator.readPassword(file));
    }
    @Test void operatorDoesNotAcceptCredentialsInsideUrls() {
        assertThrows(IllegalArgumentException.class,()->AdminOperator.dataSource(java.util.Map.of(
            "SPRING_DATASOURCE_URL","jdbc:postgresql://localhost/db?password=secret",
            "SPRING_DATASOURCE_USERNAME","test","SPRING_DATASOURCE_PASSWORD","test")));
        assertThrows(IllegalArgumentException.class,()->AdminOperator.parse(new String[]{"baseline-legacy"}));
        assertThrows(IllegalArgumentException.class,()->AdminOperator.dataSource(java.util.Map.of(
            "SPRING_DATASOURCE_URL","jdbc:postgresql://localhost/db?%70assword=secret%0Avalue",
            "SPRING_DATASOURCE_USERNAME","test","SPRING_DATASOURCE_PASSWORD","test")));
    }
}
