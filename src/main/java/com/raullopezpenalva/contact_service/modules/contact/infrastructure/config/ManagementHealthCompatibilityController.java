package com.raullopezpenalva.contact_service.modules.contact.infrastructure.config;

import java.util.Map;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Preserve the existing application-port health contract when Actuator moves. */
@RestController
@Profile("observability")
public class ManagementHealthCompatibilityController {
    private final HealthEndpoint health;
    public ManagementHealthCompatibilityController(HealthEndpoint health) { this.health = health; }
    @GetMapping("/actuator/health")
    public ResponseEntity<Map<String, String>> health() {
        String status = health.health().getStatus().getCode();
        return ResponseEntity.status(status.equals("DOWN") || status.equals("OUT_OF_SERVICE") ? 503 : 200)
            .body(Map.of("status", status));
    }
}
