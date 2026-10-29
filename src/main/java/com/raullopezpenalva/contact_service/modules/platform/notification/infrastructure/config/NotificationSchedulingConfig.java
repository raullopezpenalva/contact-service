package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(NotificationRetryProperties.class)
public class NotificationSchedulingConfig {}
