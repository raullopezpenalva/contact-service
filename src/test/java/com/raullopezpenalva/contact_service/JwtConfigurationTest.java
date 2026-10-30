package com.raullopezpenalva.contact_service;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.jwt.*;
import static org.junit.jupiter.api.Assertions.*;

class JwtConfigurationTest {
    private JwtKeyMaterial keys(){return new JwtKeyMaterial("https://admin.example.test","test",JwtTestSupport.KEYS.resolve("private.pem"),JwtTestSupport.KEYS.resolve("public"));}
    @Test void mandatoryLocalKeysRejectMissingInvalidAndUnsafeInput() throws Exception {
        Path dir=Files.createTempDirectory("invalid-jwt-");
        assertThrows(IllegalStateException.class,()->new JwtKeyMaterial("https://admin.example.test","test",dir.resolve("missing"),dir));
        assertThrows(IllegalStateException.class,()->new JwtKeyMaterial("http://admin.example.test","test",JwtTestSupport.KEYS.resolve("private.pem"),JwtTestSupport.KEYS.resolve("public")));
        assertThrows(IllegalStateException.class,()->new JwtKeyMaterial("https://user:pass@admin.example.test","test",JwtTestSupport.KEYS.resolve("private.pem"),JwtTestSupport.KEYS.resolve("public")));
        assertThrows(IllegalStateException.class,()->new JwtKeyMaterial("https://admin.example.test","unknown",JwtTestSupport.KEYS.resolve("private.pem"),JwtTestSupport.KEYS.resolve("public")));
        Path unsafe=dir.resolve("private.pem");Files.copy(JwtTestSupport.KEYS.resolve("private.pem"),unsafe);
        Files.setPosixFilePermissions(unsafe,PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(IllegalStateException.class,()->new JwtKeyMaterial("https://admin.example.test","test",unsafe,JwtTestSupport.KEYS.resolve("public")));
        Files.setPosixFilePermissions(unsafe,PosixFilePermissions.fromString("rw-------"));Files.writeString(unsafe,"invalid-private-key-contents-".repeat(4));
        var error=assertThrows(IllegalStateException.class,()->new JwtKeyMaterial("https://admin.example.test","test",unsafe,JwtTestSupport.KEYS.resolve("public")));
        assertNull(error.getCause());assertEquals("Invalid admin JWT signing configuration",error.getMessage());
        assertNotNull(keys());
    }
    @Test void injectedClockBoundsIssuerLifetimeAndSkew() {
        Instant now=Instant.parse("2026-10-30T12:00:00Z");
        var jwt=Jwt.withTokenValue("synthetic").header("alg","RS256").header("typ","at+jwt").header("kid","test")
            .issuer("https://admin.example.test").audience(List.of("contact-service-admin"))
            .subject(UUID.randomUUID().toString()).jti(UUID.randomUUID().toString()).claim("sid",UUID.randomUUID().toString())
            .claim("roles",List.of("ADMIN")).claim("authenticationVersion",0L).issuedAt(now).notBefore(now).expiresAt(now.plusSeconds(600)).build();
        assertFalse(AdminJwtConfiguration.validateClaims(jwt,keys(),Clock.fixed(now,ZoneOffset.UTC)).hasErrors());
        assertTrue(AdminJwtConfiguration.validateClaims(jwt,keys(),Clock.fixed(now.minusSeconds(31),ZoneOffset.UTC)).hasErrors());
        assertTrue(AdminJwtConfiguration.validateClaims(jwt,keys(),Clock.fixed(now.plusSeconds(631),ZoneOffset.UTC)).hasErrors());
    }
}
