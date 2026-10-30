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
            com.raullopezpenalva.contact_service.modules.platform.websecurity.application.AdminSessionService sessions,
            @Value("${app.security.local-docs-enabled:false}") boolean localDocsEnabled) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, failure) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            SecurityErrorWriter.write(request, response, 401, "Authentication required");
        };
        http
            .csrf(csrf -> csrf.disable()) // Header-only Bearer; no cookies, Basic or browser ambient credentials.
            .httpBasic(basic -> basic.disable()).formLogin(form -> form.disable())
            .logout(logout -> logout.disable()).requestCache(cache -> cache.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers(request -> request.getDispatcherType() == DispatcherType.ERROR
                        && request.getRequestURI().equals(request.getContextPath() + "/error")).permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/contact/messages").permitAll()
                    .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/auth/change-password").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/admin/contact/messages", "/api/v1/admin/contact/messages/{id}").hasRole("ADMIN")
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/admin/contact/messages/{id}/status").hasRole("ADMIN");
                if (localDocsEnabled && environment.acceptsProfiles(Profiles.of("local", "test")))
                    auth.requestMatchers(HttpMethod.GET, "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**").permitAll();
                auth.anyRequest().denyAll();
            })
            .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized)
                .accessDeniedHandler((request, response, failure) ->
                    SecurityErrorWriter.write(request, response, 403, "Access denied")))
            .oauth2ResourceServer(resource -> resource.authenticationEntryPoint(unauthorized)
                .accessDeniedHandler((request,response,failure)->SecurityErrorWriter.write(request,response,403,"Access denied"))
                .bearerTokenResolver(request -> {
                    var headers=java.util.Collections.list(request.getHeaders("Authorization"));
                    if(request.getParameter("access_token")!=null || headers.size()>1
                            || (!headers.isEmpty() && headers.getFirst().length()>8192))
                        throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_token");
                    return new org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver().resolve(request);
                })
                .jwt(jwt -> jwt.jwtAuthenticationConverter(token -> {
                    try {
                        var authorities=sessions.effectiveRoles(token).stream()
                            .map(role->new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role.name())).toList();
                        return new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(token,authorities);
                    } catch(RuntimeException invalid) {
                        throw new org.springframework.security.authentication.BadCredentialsException("Invalid access token");
                    }
                })));
        return http.build();
    }
}
