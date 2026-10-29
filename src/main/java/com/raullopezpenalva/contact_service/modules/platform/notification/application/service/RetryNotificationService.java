package com.raullopezpenalva.contact_service.modules.platform.notification.application.service;

import com.raullopezpenalva.contact_service.modules.platform.notification.application.mapper.NotificationPayloadCodec;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.model.NotificationMessage;
import com.raullopezpenalva.contact_service.modules.platform.notification.application.port.out.NotificationDeliveryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RetryNotificationService {
    private static final Logger log = LoggerFactory.getLogger(RetryNotificationService.class);
    private final NotificationDeliveryRepository repository;
    private final NotificationService notificationService;
    private final NotificationPayloadCodec payloadCodec;
    private final boolean enabled;
    private final int maxAttempts;
    private final int batchSize;

    public RetryNotificationService(NotificationDeliveryRepository repository,
            NotificationService notificationService, NotificationPayloadCodec payloadCodec,
            @Value("${app.notifications.retry.enabled:true}") boolean enabled,
            @Value("${app.notification.enabled:true}") boolean notificationsEnabled,
            @Value("${app.notifications.retry.max-attempts:3}") int maxAttempts,
            @Value("${app.notifications.retry.batch-size:100}") int batchSize) {
        this.repository = repository;
        this.notificationService = notificationService;
        this.payloadCodec = payloadCodec;
        this.enabled = enabled && notificationsEnabled;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
    }

    // A single process cannot overlap scheduled/manual batches. Multi-replica claims are out of scope.
    public synchronized void retryFailedDeliveries() {
        if (!enabled) return;
        for (var delivery : repository.findRetryable(maxAttempts, batchSize)) {
            if (!delivery.canRetry(maxAttempts)) continue;
            NotificationMessage message;
            try {
                message = payloadCodec.decode(delivery);
            } catch (IllegalArgumentException ex) {
                delivery.incrementAttempts();
                delivery.markAsFailed("Invalid versioned notification snapshot");
                repository.save(delivery);
                log.warn("Cannot reconstruct notification deliveryId={}", delivery.getId());
                continue;
            }
            try {
                log.info("Retrying deliveryId={}, channel={}, attempt={}",
                    delivery.getId(), delivery.getChannel(), delivery.getAttempts() + 1);
                notificationService.sendNotification(message, delivery);
            } catch (Exception ex) {
                // Continue other deliveries; infrastructure failures are retried on the next scan.
                log.error("Retry processing failed for deliveryId={}, errorType={}",
                    delivery.getId(), ex.getClass().getSimpleName());
            }
        }
    }
}
