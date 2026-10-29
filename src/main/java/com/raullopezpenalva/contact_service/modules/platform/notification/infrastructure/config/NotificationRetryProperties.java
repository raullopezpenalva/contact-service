package com.raullopezpenalva.contact_service.modules.platform.notification.infrastructure.config;

import java.time.Duration;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.notifications.retry")
@Validated
public class NotificationRetryProperties {
    private boolean enabled = true;
    @Min(1) private int maxAttempts = 3;
    @Min(1) @Max(1000) private int batchSize = 100;
    private Duration interval = Duration.ofSeconds(30);

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public Duration getInterval() { return interval; }
    public void setInterval(Duration interval) { this.interval = interval; }
    @AssertTrue(message = "Retry interval must be at least one millisecond")
    public boolean isIntervalValid() { return interval != null && interval.compareTo(Duration.ofMillis(1)) >= 0; }
}
