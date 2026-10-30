package com.raullopezpenalva.contact_service;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.application.*;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.jwt.*;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.emailSender.EmailClient;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram.TelegramClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:jwt;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","app.notification.enabled=false"})
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AdminJwtIT extends JwtTestSupport {
    static final String ADMIN="/api/v1/admin/contact/messages";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtKeyMaterial keys;
    @Autowired AdminSessionService sessions;
    @MockitoBean EmailClient email;
    @MockitoBean TelegramClient telegram;
    private String loginName;
    @BeforeEach void account(){loginName="admin-"+UUID.randomUUID();adminAccounts.provision(loginName,PASSWORD.toCharArray());}
    private String token(){return adminLogin.login(loginName,PASSWORD.toCharArray());}
    private String credentials(String username,String password) throws Exception {return json.writeValueAsString(Map.of("username",username,"password",password));}
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path,String token) {
        return get(path).header("Authorization","Bearer "+token);
    }
    private String sign(String original,java.util.function.Consumer<JWTClaimsSet.Builder> change,JWSHeader header) throws Exception {
        var builder=new JWTClaimsSet.Builder(SignedJWT.parse(original).getJWTClaimsSet());change.accept(builder);
        var signed=new SignedJWT(header,builder.build());
        signed.sign(new RSASSASigner((RSAKey)keys.signingKeys().getKeys().getFirst()));return signed.serialize();
    }
    private JWSHeader jwtHeader(String kid){return new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(kid).type(new JOSEObjectType("at+jwt")).build();}
    @Test void loginIssuesMinimalShortTokenAndHeadersWithoutSecrets(CapturedOutput output) throws Exception {
        var result=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(loginName,PASSWORD)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Pragma","no-cache")).andExpect(cookie().doesNotExist("JSESSIONID"))
            .andExpect(jsonPath("$.expires_in").value(600)).andExpect(jsonPath("$.token_type").value("Bearer"))
            .andExpect(jsonPath("$.refresh_token").doesNotExist()).andReturn();
        String token=json.readTree(result.getResponse().getContentAsString()).get("access_token").asText();
        var claims=SignedJWT.parse(token).getJWTClaimsSet();
        assertEquals(600,Duration.between(claims.getIssueTime().toInstant(),claims.getExpirationTime().toInstant()).toSeconds());
        assertEquals(Set.of("iss","aud","sub","jti","sid","iat","nbf","exp","roles","authenticationVersion"),claims.getClaims().keySet());
        assertFalse(output.getAll().contains(token));assertFalse(output.getAll().contains(PASSWORD));
        mvc.perform(request("/api/v1/auth/me",token)).andExpect(status().isOk()).andExpect(jsonPath("$.login").value(loginName))
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(request(ADMIN,token)).andExpect(status().isOk());
    }
    @Test void unknownDisabledAndWrongCredentialsHaveSameFailure() throws Exception {
        adminAccounts.provision("backup-"+UUID.randomUUID(),PASSWORD.toCharArray());
        for(String name:List.of("unknown-"+UUID.randomUUID(),loginName)) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(name,"Wrong synthetic password!")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid credentials"));
        }
        adminAccounts.setEnabled(loginName,false);
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(credentials(loginName,PASSWORD)))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").value("Invalid credentials"));
    }
    @Test void rejectsCryptographicClaimHeaderAndSessionConfusion() throws Exception {
        String token=token();
        List<String> rejected=new ArrayList<>();
        rejected.add("malformed");
        rejected.add(sign(token,b->b.issuer("https://wrong.example"),jwtHeader("test")));
        rejected.add(sign(token,b->b.audience(List.of("wrong")),jwtHeader("test")));
        rejected.add(sign(token,b->b.subject(UUID.randomUUID().toString()),jwtHeader("test")));
        rejected.add(sign(token,b->b.claim("sid",UUID.randomUUID().toString()),jwtHeader("test")));
        rejected.add(sign(token,b->b.jwtID(UUID.randomUUID().toString()),jwtHeader("test")));
        rejected.add(sign(token,b->b.claim("authenticationVersion",99),jwtHeader("test")));
        rejected.add(sign(token,b->b.claim("authenticationVersion",0.5),jwtHeader("test")));
        rejected.add(sign(token,b->b.claim("roles",List.of("SUPERUSER")),jwtHeader("test")));
        rejected.add(sign(token,b->b.claim("roles",null),jwtHeader("test")));
        rejected.add(sign(token,b->b.expirationTime(null),jwtHeader("test")));
        rejected.add(sign(token,b->b.issueTime(Date.from(Instant.now().plusSeconds(100))).notBeforeTime(Date.from(Instant.now().plusSeconds(100)))
            .expirationTime(Date.from(Instant.now().plusSeconds(700))),jwtHeader("test")));
        rejected.add(sign(token,b->b.issueTime(Date.from(Instant.now().minusSeconds(1000))).notBeforeTime(Date.from(Instant.now().minusSeconds(1000)))
            .expirationTime(Date.from(Instant.now().minusSeconds(400))),jwtHeader("test")));
        rejected.add(sign(token,b->b.expirationTime(Date.from(Instant.now().plusSeconds(3600))),jwtHeader("test")));
        rejected.add(sign(token,b->{},jwtHeader("retired")));
        rejected.add(sign(token,b->{},new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").type(JOSEObjectType.JWT).build()));
        rejected.add(sign(token,b->{},new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").type(new JOSEObjectType("at+jwt"))
            .jwkURL(java.net.URI.create("https://untrusted.invalid/keys")).build()));
        var hs=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),SignedJWT.parse(token).getJWTClaimsSet());
        hs.sign(new MACSigner(new byte[32]));rejected.add(hs.serialize());
        rejected.add(new PlainJWT(SignedJWT.parse(token).getJWTClaimsSet()).serialize());
        var wrong=new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(3072).keyID("test").generate();
        var forged=new SignedJWT(jwtHeader("test"),SignedJWT.parse(token).getJWTClaimsSet());forged.sign(new RSASSASigner(wrong));rejected.add(forged.serialize());
        for(String candidate:rejected) mvc.perform(request(ADMIN,candidate)).andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate","Bearer")).andExpect(jsonPath("$.message").value("Authentication required"));
        mvc.perform(request(ADMIN,token)).andExpect(status().isOk());
    }
    @Test void signedRolesCannotEscalateAndTransportCannotBypassAuthentication() throws Exception {
        String token=token();
        String noRole=sign(token,b->b.claim("roles",List.of()),jwtHeader("test"));
        mvc.perform(request(ADMIN,noRole)).andExpect(status().isForbidden());
        var created=mvc.perform(post("/api/v1/contact/messages").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"jwt-fixture@example.com\",\"subject\":\"Authorization test\",\"content\":\"A synthetic protected update fixture.\"}"))
            .andExpect(status().isCreated()).andReturn();
        String id=json.readTree(created.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(request(ADMIN+"/"+id,noRole)).andExpect(status().isForbidden());
        mvc.perform(patch(ADMIN+"/"+id+"/status").header("Authorization","Bearer "+noRole).contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"SPAM\"}")).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN+"/"+id).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("test","test")))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch(ADMIN+"/"+id+"/status").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("test","test"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SPAM\"}")).andExpect(status().isUnauthorized());
        mvc.perform(request(ADMIN+"/"+id,token)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NEW"));

        mvc.perform(get(ADMIN).param("access_token",token)).andExpect(status().isUnauthorized());
        mvc.perform(request(ADMIN,token).param("access_token",token)).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN).cookie(new jakarta.servlet.http.Cookie("access_token",token))).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("test","test")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN).header("Authorization","Bearer "+token,"Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(post(ADMIN).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(request(ADMIN,token).header("Origin","https://evil.example")).andExpect(status().isForbidden());
        mvc.perform(request(ADMIN,token).header("Origin","https://admin.example.test")).andExpect(status().isOk());
    }
    @Test void logoutPasswordResetDisableAndRoleChangeRevokeOnNextRequest() throws Exception {
        String first=token(),second=token();
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+first)).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+first)).andExpect(status().isUnauthorized());
        mvc.perform(request(ADMIN,second)).andExpect(status().isOk());
        adminAccounts.resetPassword(loginName,PASSWORD.toCharArray());
        mvc.perform(request(ADMIN,second)).andExpect(status().isUnauthorized());
        String third=token();adminAccounts.setEnabled(loginName,false);
        mvc.perform(request(ADMIN,third)).andExpect(status().isUnauthorized());
        adminAccounts.setEnabled(loginName,true);String fourth=token();
        adminAccounts.setRoles(loginName,Set.of());mvc.perform(request(ADMIN,fourth)).andExpect(status().isUnauthorized());
    }
    @Test void passwordChangeIsAtomicAndRevokesAllSessions() throws Exception {
        String first=token(),second=token();String replacement="Replacement synthetic password 47!";
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization","Bearer "+first).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("currentPassword","Wrong synthetic password!","newPassword",replacement))))
            .andExpect(status().isUnauthorized());
        mvc.perform(request(ADMIN,second)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization","Bearer "+first).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("currentPassword",PASSWORD,"newPassword",replacement))))
            .andExpect(status().isNoContent());
        mvc.perform(request(ADMIN,first)).andExpect(status().isUnauthorized());mvc.perform(request(ADMIN,second)).andExpect(status().isUnauthorized());
        assertThrows(org.springframework.security.core.AuthenticationException.class,()->token());
        assertNotNull(adminLogin.login(loginName,replacement.toCharArray()));
    }
    @Test void loginQuotaBodyAndOriginPoliciesAreIndependent() throws Exception {
        for(int i=0;i<10;i++) mvc.perform(post("/api/v1/auth/login").with(r->{r.setRemoteAddr("192.0.2.45");return r;})
            .contentType(MediaType.APPLICATION_JSON).content(credentials(loginName,"Wrong synthetic password!"))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").with(r->{r.setRemoteAddr("192.0.2.45");return r;})
            .contentType(MediaType.APPLICATION_JSON).content(credentials(loginName,PASSWORD))).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));
        mvc.perform(post("/api/v1/auth/login").with(r->{r.setRemoteAddr("192.0.2.46");return r;})
            .contentType(MediaType.APPLICATION_JSON).content("x".repeat(8193))).andExpect(status().isPayloadTooLarge());
        for(String origin:List.of("null","https://evil.example","https://admin.example.test.evil.example"))
            mvc.perform(post("/api/v1/auth/login").with(r->{r.setRemoteAddr("192.0.2.47");return r;})
                .header("Origin",origin).contentType(MediaType.APPLICATION_JSON).content(credentials(loginName,PASSWORD))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/login").with(r->{r.setRemoteAddr("192.0.2.48");return r;})
            .contentType(MediaType.TEXT_PLAIN).content("invalid")).andExpect(status().isUnsupportedMediaType());
    }
    @Test void sessionCapAndKeyRevocationUsePersistentState() throws Exception {
        String token=token();for(int i=1;i<10;i++) token();
        assertThrows(AuthFailure.class,()->token());
        assertTrue(adminAccounts.revokeKey("test")>=10);
        mvc.perform(request(ADMIN,token)).andExpect(status().isUnauthorized());
        assertNotNull(token());adminAccounts.revokeSessions(loginName);
        assertNotNull(token());
    }
    @Test void malformedCredentialShapesAndInvalidPasswordChangesDoNotMutate() throws Exception {
        String token=token();
        for(String body:List.of("{}","{\"username\":123,\"password\":\"long enough synthetic password\"}",
                "{\"username\":\"admin\",\"password\":[\"long enough synthetic password\"]}",
                "{\"username\":\"admin\",\"password\":\"short\"}","{\"username\":\"admin\",\"password\":null}"))
            mvc.perform(post("/api/v1/auth/login").with(r->{r.setRemoteAddr("192.0.2.49");return r;})
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("currentPassword",PASSWORD,"newPassword","short")))).andExpect(status().isBadRequest());
        mvc.perform(request(ADMIN,token)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/login").header("Origin","https://admin.example.test","https://admin.example.test")
            .contentType(MediaType.APPLICATION_JSON).content(credentials(loginName,PASSWORD))).andExpect(status().isForbidden());
    }
}
