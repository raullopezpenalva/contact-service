package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationFailureLogger {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationFailureLogger.class);
    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        // Never include credentials, authorization headers or user-supplied usernames.
        log.warn("Admin authentication failed requestId={}, errorType={}",
            org.slf4j.MDC.get("requestId"), event.getException().getClass().getSimpleName());
    }
}
