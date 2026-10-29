package com.raullopezpenalva.contact_service.modules.contact.infrastructure.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http.SecurityErrorWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public WebSecurityCustomizer rejectedRequestPolicy() {
        return web -> web.requestRejectedHandler((request, response, failure) ->
            SecurityErrorWriter.write(request, response, 400, "Invalid request"));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, Environment environment,
            @Value("${app.security.local-docs-enabled:false}") boolean localDocsEnabled) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, failure) -> {
            response.setHeader("WWW-Authenticate", "Basic realm=\"admin\"");
            SecurityErrorWriter.write(request, response, 401, "Authentication required");
        };
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(request -> request.getDispatcherType() == DispatcherType.ERROR
                        && request.getRequestURI().equals(request.getContextPath() + "/error")).permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/contact/messages").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/admin/contact/messages", "/api/v1/admin/contact/messages/{id}").authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/contact/messages/{id}/status").authenticated();
                if (localDocsEnabled && environment.acceptsProfiles(Profiles.of("local", "test")))
                    auth.requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll();
                auth.anyRequest().denyAll();
            })
            .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized)
                .accessDeniedHandler((request, response, failure) ->
                    SecurityErrorWriter.write(request, response, 403, "Access denied")))
            .httpBasic(basic -> basic.authenticationEntryPoint(unauthorized));
        return http.build();
    }
}
