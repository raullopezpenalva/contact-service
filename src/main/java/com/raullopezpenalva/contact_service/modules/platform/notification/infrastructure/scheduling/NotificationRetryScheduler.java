package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.scheduling;

import com.raullopezpenalva.contact_service.modules.platform.notification.application.service.RetryNotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = {"app.notifications.retry.enabled", "app.notification.enabled"},
    havingValue = "true", matchIfMissing = true)
public class NotificationRetryScheduler {
    private final RetryNotificationService retryService;

    public NotificationRetryScheduler(RetryNotificationService retryService) {
        this.retryService = retryService;
    }

    @Scheduled(fixedDelayString = "${app.notifications.retry.interval:30s}",
               initialDelayString = "${app.notifications.retry.interval:30s}")
    public void retry() {
        retryService.retryFailedDeliveries();
    }
}
