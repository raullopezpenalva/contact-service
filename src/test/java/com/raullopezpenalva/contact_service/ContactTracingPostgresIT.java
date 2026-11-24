package com.raullopezpenalva.contact_service;

import com.raullopezpenalva.contact_service.shared.observability.ContactTracing;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.emailSender.EmailClient;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram.TelegramClient;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.api.common.*;
import io.opentelemetry.sdk.trace.*;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.*;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.awaitility.Awaitility.await;

@Testcontainers
@Tag("postgres")
@SpringBootTest(properties={"spring.flyway.enabled=true","spring.jpa.hibernate.ddl-auto=validate",
    "app.notifications.channels=EMAIL,TELEGRAM","app.security.rate-limit.contactrequest.max-requests=1000"})
@AutoConfigureMockMvc(print=org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import(ContactTracingPostgresIT.TracingFixture.class)
class ContactTracingPostgresIT extends JwtTestSupport {
    static final List<SpanData> spans = new CopyOnWriteArrayList<>();
    @Container static final PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",pg::getJdbcUrl);p.add("spring.datasource.username",pg::getUsername);
        p.add("spring.datasource.password",pg::getPassword);p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        p.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
    }
    @TestConfiguration(proxyBeanMethods=false) static class TracingFixture {
        @Bean @Primary ContactTracing capturedTracing() {
            SpanExporter captured = new SpanExporter() {
                public CompletableResultCode export(Collection<SpanData> s) { spans.addAll(s); return CompletableResultCode.ofSuccess(); }
                public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
                public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
            };
            var builder = SdkTracerProvider.builder().setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"),"contact-service")))
                .addSpanProcessor(SimpleSpanProcessor.create(captured));
            String endpoint = System.getenv("CONTACT_TRACING_IT_ENDPOINT");
            if (endpoint != null) builder.addSpanProcessor(BatchSpanProcessor.builder(OtlpHttpSpanExporter.builder().setEndpoint(endpoint).build())
                .setScheduleDelay(Duration.ofMillis(100)).build());
            var provider = builder.build();
            return new ContactTracing(OpenTelemetrySdk.builder().setTracerProvider(provider).build(),provider);
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ContactTracing tracing;
    @MockitoBean EmailClient email;
    @MockitoBean TelegramClient telegram;

    @Test void realFlowExportsSafeRelatedSpansAndIgnoresUntrustedHeaders() throws Exception {
        spans.clear();
        doAnswer(inv -> { TimeUnit.MILLISECONDS.sleep(200); return null; }).when(email).sendEmail(any(),any(),any());
        doThrow(new IllegalStateException("TOKEN-FORM-CANARY")).when(telegram).sendMessage(any());
        String supplied = "11111111111111111111111111111111";
        mvc.perform(post("/api/v1/contact/messages").contentType(MediaType.APPLICATION_JSON)
            .header("traceparent","00-"+supplied+"-1111111111111111-01").header("baggage","secret=TOKEN-FORM-CANARY")
            .content("{\"email\":\"canary@example.test\",\"subject\":\"TOKEN-FORM-CANARY\",\"content\":\"This is private test content\"}"))
            .andExpect(status().isCreated());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertTrue(spans.stream().anyMatch(s -> s.getName().equals("notification.process"))));
        tracing.flush();
        Set<String> names = new HashSet<>();spans.forEach(s -> names.add(s.getName()));
        assertTrue(names.containsAll(Set.of("contact.http","contact.persist","notification.queue","notification.process","notification.email","notification.telegram")),names.toString());
        assertEquals(1,spans.stream().map(SpanData::getTraceId).distinct().count());
        var root=spans.stream().filter(s -> s.getName().equals("contact.http")).findFirst().orElseThrow();
        assertNotEquals(supplied,root.getTraceId());
        for (var span : spans) {
            assertTrue(span.getAttributes().isEmpty());assertTrue(span.getEvents().isEmpty());assertTrue(span.getLinks().isEmpty());
            assertEquals("",span.getStatus().getDescription());
        }
        var slow=spans.stream().filter(s -> s.getName().equals("notification.email")).findFirst().orElseThrow();
        assertTrue(slow.getEndEpochNanos()-slow.getStartEpochNanos()>=TimeUnit.MILLISECONDS.toNanos(180));
        assertEquals(io.opentelemetry.api.trace.StatusCode.ERROR,spans.stream().filter(s -> s.getName().equals("notification.telegram")).findFirst().orElseThrow().getStatus().getStatusCode());
        // Optional real native pipeline acceptance: same actual Java trace must be queryable in Tempo.
        String tempo=System.getenv("CONTACT_TRACING_IT_TEMPO");
        if (tempo!=null) await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(tempo+"/api/traces/"+root.getTraceId())).header("Accept","application/json").build();
            var response=java.net.http.HttpClient.newHttpClient().send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200,response.statusCode());
            assertTrue(response.body().contains("notification.telegram"));assertFalse(response.body().contains("CANARY"));
            assertFalse(response.body().contains("canary@example.test"));
        });
        int previous=spans.size();mvc.perform(get("/actuator/health")).andExpect(status().isOk());assertEquals(previous,spans.size());
    }
}
