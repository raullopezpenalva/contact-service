package com.raullopezpenalva.contact_service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.raullopezpenalva.contact_service.modules.contact.infrastructure.repository.ContactMessageRepository;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.emailSender.EmailClient;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram.TelegramClient;
import com.raullopezpenalva.contact_service.shared.events.EventPublisher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"app.security.rate-limit.contactrequest.max-requests=2",
    "app.security.rate-limit.admin.max-requests=100", "app.security.trusted-proxies=10.0.0.2,2001:db8::2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SecurityContractIT {
    private static final String PUBLIC = "/api/v1/contact/messages";
    private static final String ADMIN = "/api/v1/admin/contact/messages";
    private static final String BODY = "{\"email\":\"person@example.com\",\"subject\":\"Hello\",\"content\":\"A meaningful message body.\"}";
    private static final String SECRET = "private-password-token-SQL-fixture";
    private static final AtomicInteger CLIENT = new AtomicInteger(20);
    @Autowired MockMvc mvc;
    @Autowired ContactMessageRepository repository;
    @Autowired ObjectMapper json;
    @MockitoBean EmailClient email;
    @MockitoBean TelegramClient telegram;
    @MockitoBean EventPublisher events;
    @BeforeEach void clean() { repository.deleteAll(); }

    MockHttpServletRequestBuilder create(String peer) {
        return post(PUBLIC).with(r -> {r.setRemoteAddr(peer); return r;})
            .contentType("application/json").content(BODY);
    }
    String nextClient() { return "198.51.100." + CLIENT.incrementAndGet(); }

    void contract(MvcResult result, int status) throws Exception {
        var body = json.readTree(result.getResponse().getContentAsString());
        assertEquals(status,body.get("status").asInt());
        assertTrue(body.get("details").isArray());
        assertFalse(body.get("timestamp").asText().isEmpty());
        assertFalse(body.get("error").asText().isEmpty());
        assertFalse(body.get("message").asText().isEmpty());
        assertFalse(body.get("path").asText().isEmpty());
        String id=body.get("requestId").asText();
        assertDoesNotThrow(() -> UUID.fromString(id));
        assertEquals(id,result.getResponse().getHeader("X-Request-ID"));
        assertEquals("no-store",result.getResponse().getHeader("Cache-Control"));
        assertFalse(result.getResponse().getContentAsString().contains(SECRET));
    }

    @Test void untrustedHeadersCannotAlterStoredIdentityOrQuota() throws Exception {
        String peer=nextClient();
        for(int i=0;i<3;i++) {
            var result=mvc.perform(create(peer).header("X-Real-IP","192.0.2."+i)
                .header("X-Forwarded-For",SECRET).header("Forwarded","for=192.0.2."+i))
                .andExpect(status().is(i<2?201:429)).andReturn();
            if(i==2) {contract(result,429); assertEquals("3600",result.getResponse().getHeader("Retry-After"));}
        }
        assertEquals(2,repository.count());
        assertTrue(repository.findAll().stream().allMatch(m -> peer.equals(m.getSourceIP())));
    }

    @ParameterizedTest @ValueSource(strings={"10.0.0.2","2001:db8::2"})
    void trustedProxyCanonicalizesIpv6ForBothStorageAndQuotas(String peer) throws Exception {
        int n=CLIENT.incrementAndGet();
        String compact="2001:db8::"+n;
        String expanded="2001:0db8:0000:0000:0000:0000:0000:"+n;
        mvc.perform(create(peer).header("X-Real-IP",compact).header("X-Forwarded-For",SECRET))
            .andExpect(status().isCreated());
        mvc.perform(create(peer).header("X-Real-IP",expanded)).andExpect(status().isCreated());
        mvc.perform(create(peer).header("X-Real-IP",compact)).andExpect(status().isTooManyRequests());
        assertEquals(2,repository.count());
        assertTrue(repository.findAll().stream().allMatch(m -> ("2001:db8:0:0:0:0:0:"+n).equals(m.getSourceIP())));
    }

    @Test void malformedAndMultipleRealIpHeadersFallBackToTheOriginalPeer() throws Exception {
        mvc.perform(create("10.0.0.2").header("X-Real-IP","999.1.1.1"))
            .andExpect(status().isCreated());
        mvc.perform(create("10.0.0.2").header("X-Real-IP","192.0.2.1","192.0.2.2"))
            .andExpect(status().isCreated());
        assertTrue(repository.findAll().stream().allMatch(m -> "10.0.0.2".equals(m.getSourceIP())));
        mvc.perform(create("10.0.0.2").header("X-Real-IP","example.com"))
            .andExpect(status().isTooManyRequests());
    }

    @Test void trustedIpv4ClientIsPersistedInsteadOfTheProxyOrForwardedHeader() throws Exception {
        mvc.perform(create("10.0.0.2").header("X-Real-IP","192.0.2.88").header("X-Forwarded-For",SECRET))
            .andExpect(status().isCreated());
        assertEquals("192.0.2.88",repository.findAll().getFirst().getSourceIP());
    }

    @Test void malformedInputAndParametersOmitRejectedValuesAndParserDetails(CapturedOutput output) throws Exception {
        var malformed=mvc.perform(create(nextClient()).header("X-Request-ID",SECRET)
            .content("{\"email\":\""+SECRET+"\"")).andExpect(status().isBadRequest()).andReturn();
        contract(malformed,400);
        assertEquals("Malformed JSON request",json.readTree(malformed.getResponse().getContentAsString()).get("message").asText());
        var parameter=mvc.perform(get(ADMIN).param("status",SECRET).with(httpBasic("test","test")))
            .andExpect(status().isBadRequest()).andReturn();
        contract(parameter,400);
        assertFalse(output.getAll().contains(SECRET));
        assertTrue(output.getAll().contains("HttpMessageNotReadableException"));
        assertTrue(output.getAll().contains(malformed.getResponse().getHeader("X-Request-ID")));
        assertNull(org.slf4j.MDC.get("requestId"));
        assertEquals(0,repository.count());verifyNoInteractions(events,email,telegram);
    }

    @Test void unexpectedFailuresRetainSafeDiagnosticCorrelation(CapturedOutput output) throws Exception {
        doThrow(new IllegalStateException(SECRET)).when(events).publish(any());
        var result=mvc.perform(create(nextClient())).andExpect(status().isInternalServerError()).andReturn();
        contract(result,500);
        assertFalse(output.getAll().contains(SECRET));
        assertTrue(output.getAll().contains("IllegalStateException"));
        assertTrue(output.getAll().contains(result.getResponse().getHeader("X-Request-ID")));
        verifyNoInteractions(email,telegram);
    }

    @Test void securityAndBodyFailuresUseTheSameSafeContract() throws Exception {
        contract(mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized()).andReturn(),401);
        contract(mvc.perform(get(ADMIN).with(httpBasic("test",SECRET))).andExpect(status().isUnauthorized()).andReturn(),401);
        contract(mvc.perform(get("/internal/"+SECRET).with(httpBasic("test","test")))
            .andExpect(status().isForbidden()).andReturn(),403);
        contract(mvc.perform(create(nextClient()).content("x".repeat(16385)))
            .andExpect(status().isPayloadTooLarge()).andReturn(),413);
        contract(mvc.perform(create(nextClient()).contentType("text/plain"))
            .andExpect(status().isUnsupportedMediaType()).andReturn(),415);
        contract(mvc.perform(get(ADMIN+"/"+UUID.randomUUID()).with(httpBasic("test","test")))
            .andExpect(status().isNotFound()).andReturn(),404);
    }

    @ParameterizedTest @ValueSource(strings={"/actuator/env","/actuator/health/liveness","/v3/api-docs","/swagger-ui/index.html","/error","/unknown"})
    void undeclaredRoutesAreNotPublic(String path) throws Exception {
        contract(mvc.perform(get(path)).andExpect(status().isUnauthorized()).andReturn(),401);
    }

    @Test void alternateMethodsAndForwardDispatchCannotBypassThePolicy() throws Exception {
        mvc.perform(get(PUBLIC)).andExpect(status().isUnauthorized());
        mvc.perform(post(ADMIN).with(httpBasic("test","test"))).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN).with(r -> {r.setDispatcherType(DispatcherType.FORWARD); return r;}))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN).with(r -> {r.setDispatcherType(DispatcherType.ERROR); return r;}))
            .andExpect(status().isUnauthorized());
        var error=mvc.perform(get("/error").with(r -> {r.setDispatcherType(DispatcherType.ERROR);return r;})
            .requestAttr(RequestDispatcher.ERROR_STATUS_CODE,500)
            .requestAttr(RequestDispatcher.ERROR_MESSAGE,SECRET)).andExpect(status().isInternalServerError()).andReturn();
        contract(error,500);
    }

    @Test void publicHealthContainsOnlyStatus() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist()).andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test void firewallRejectionsUseSafeErrors() throws Exception {
        var result=mvc.perform(get("/api/v1/contact/%2e%2e/"+SECRET))
            .andExpect(status().isBadRequest()).andReturn();
        contract(result,400);
    }
}
