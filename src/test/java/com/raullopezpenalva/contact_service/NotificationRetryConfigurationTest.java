package com.raullopezpenalva.contact_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.service.RetryNotificationService;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.config.*;
import com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.scheduling.NotificationRetryScheduler;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationRetryConfigurationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .withUserConfiguration(NotificationSchedulingConfig.class, NotificationRetryScheduler.class)
        .withBean(RetryNotificationService.class, () -> mock(RetryNotificationService.class));

    @Test void defaultsEnableSchedulerAndUseThreeTotalAttempts() {
        context.run(ctx -> {
            assertThat(ctx).hasNotFailed().hasSingleBean(NotificationRetryScheduler.class);
            var properties = ctx.getBean(NotificationRetryProperties.class);
            assertThat(properties.getMaxAttempts()).isEqualTo(3);
            assertThat(properties.getInterval()).isEqualTo(java.time.Duration.ofSeconds(30));
        });
    }

    @Test void retryToggleRemovesScheduler() {
        context.withPropertyValues("app.notifications.retry.enabled=false")
            .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(NotificationRetryScheduler.class));
    }

    @Test void globalNotificationToggleRemovesScheduler() {
        context.withPropertyValues("app.notification.enabled=false")
            .run(ctx -> assertThat(ctx).hasNotFailed().doesNotHaveBean(NotificationRetryScheduler.class));
    }

    @Test void invalidLimitsFailStartup() {
        for (String property : new String[] {"app.notifications.retry.max-attempts=0",
                "app.notifications.retry.batch-size=0", "app.notifications.retry.interval=0ms"}) {
            context.withPropertyValues(property).run(ctx -> assertThat(ctx).hasFailed());
        }
    }
}
