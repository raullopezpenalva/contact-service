package com.raullopezpenalva.contact_service.modules.contact.infrastructure.config;

import java.util.Set;
import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http.RequestMetadata;

/** Read-only metrics on a distinct, explicitly enabled management socket. */
@Configuration
@Profile("observability")
public class ObservabilityConfig {
    @Bean
    @Order(0)
    SecurityFilterChain managementMetricsSecurity(HttpSecurity http, Environment environment) throws Exception {
        http.securityMatcher(request -> {
            int main = environment.getProperty("local.server.port", Integer.class,
                environment.getProperty("server.port", Integer.class, 8080));
            int management = environment.getProperty("local.management.port", Integer.class,
                environment.getProperty("management.server.port", Integer.class, 19090));
            return request.getLocalPort() == management && management != main;
        }).csrf(csrf -> csrf.disable())
          .httpBasic(basic -> basic.disable()).formLogin(form -> form.disable())
          .logout(logout -> logout.disable()).requestCache(cache -> cache.disable())
          .sessionManagement(session -> session.sessionCreationPolicy(
              org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(auth -> auth
              .requestMatchers(org.springframework.http.HttpMethod.GET, "/actuator/prometheus", "/actuator/health").permitAll()
              .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    ServerRequestObservationConvention boundedHttpObservations(Environment environment) {
        return new DefaultServerRequestObservationConvention() {
            private final Set<String> methods = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
            @Override
            public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
                var request = context.getCarrier();
                var response = context.getResponse();
                String method = request == null ? "OTHER" : request.getMethod();
                if (!methods.contains(method)) method = "OTHER";
                int status = response == null ? 0 : response.getStatus();
                int main = environment.getProperty("local.server.port", Integer.class,
                    environment.getProperty("server.port", Integer.class, 8080));
                String listener = request == null ? "unknown" : request.getLocalPort() == main ? "application" : "management";
                return KeyValues.of("listener", listener, "method", method,
                    "uri", request == null ? "/" : RequestMetadata.path(request),
                    "status", status >= 100 && status <= 599 ? Integer.toString(status) : "UNKNOWN",
                    "outcome", status >= 100 && status <= 599 ? status / 100 + "xx" : "UNKNOWN");
            }
            @Override
            public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
                return KeyValues.empty();
            }
        };
    }

    @Bean
    MeterFilter selectedMetrics() {
        return MeterFilter.deny(id -> !id.getName().startsWith("jvm.")
            && !id.getName().startsWith("process.") && !id.getName().startsWith("system.cpu.")
            && !id.getName().startsWith("http.server.requests") && !id.getName().startsWith("hikaricp.")
            && !id.getName().startsWith("jdbc.connections.") && !id.getName().startsWith("application."));
    }
}
