package com.raullopezpenalva.contact_service.modules.contact.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
public class OpenApiSecurityConfig {
    @Bean OpenAPI adminBearerScheme() {
        return new OpenAPI().components(new Components().addSecuritySchemes("adminBearer",
            new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                .description("Short-lived admin JWT; requires a live account/session and private ingress")));
    }
    @Bean OpenApiCustomizer adminRouteSecurity() {
        return api->{if(api.getPaths()!=null) api.getPaths().forEach((path,item)->{
            if(path.startsWith("/api/v1/admin/") || (path.startsWith("/api/v1/auth/") && !path.equals("/api/v1/auth/login")))
                item.readOperations().forEach(operation->operation.addSecurityItem(new SecurityRequirement().addList("adminBearer")));
        });};
    }
}
