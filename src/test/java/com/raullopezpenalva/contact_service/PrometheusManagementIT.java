package com.raullopezpenalva.contact_service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties={"management.server.port=0", "management.server.address=127.0.0.1"})
@ActiveProfiles({"test", "observability"})
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
class PrometheusManagementIT extends JwtTestSupport {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.emailSender.EmailClient email;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.telegram.TelegramClient telegram;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.raullopezpenalva.contact_service.shared.events.EventPublisher events;
    @Autowired Environment environment;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private HttpResponse<String> request(int port,String path,String method) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
            .timeout(Duration.ofSeconds(5)).method(method,HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString());
    }
    private int port(String name) {return environment.getRequiredProperty(name,Integer.class);}

    @Test void managementSeparationPreservesPublicHealthAndExportsOnlySelectedMetrics() throws Exception {
        int main=port("local.server.port"), management=port("local.management.port");
        assertNotEquals(main,management);
        var health=request(main,"/actuator/health","GET");
        assertEquals(200,health.statusCode());
        assertEquals("{\"status\":\"UP\"}",health.body());
        assertNotEquals(200,request(main,"/actuator/prometheus","GET").statusCode());
        assertNotEquals(200,request(management,"/actuator/env","GET").statusCode());
        assertNotEquals(200,request(management,"/actuator/prometheus","POST").statusCode());
        var metrics=request(management,"/actuator/prometheus","GET");
        assertEquals(200,metrics.statusCode());
        assertTrue(metrics.body().contains("jvm_memory_used_bytes"));
        assertTrue(metrics.body().contains("hikaricp_connections"));
        assertFalse(metrics.body().contains("spring_security_"));
    }

    @Test void unknownPathsAndCallerDiagnosticsNeverBecomeMetricLabels() throws Exception {
        int main=port("local.server.port"), management=port("local.management.port");
        request(main,"/unrecognized/private-fixture?token=secret-fixture","GET");
        request(main,"/api/v1/admin/contact/messages/private-fixture","GET");
        var body=request(management,"/actuator/prometheus","GET").body();
        assertTrue(body.contains("http_server_requests_seconds_count"));
        assertTrue(body.contains("uri=\"/api/v1/admin/contact/messages/{id}\""));
        assertFalse(body.contains("private-fixture"));
        assertFalse(body.contains("secret-fixture"));
        assertFalse(body.contains("127.0.0.1"));
        assertFalse(body.contains("exception="));
    }
}
