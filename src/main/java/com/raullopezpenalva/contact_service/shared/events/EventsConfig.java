package com.raullopezpenalva.contact_service.shared.events;

import com.raullopezpenalva.contact_service.shared.observability.ContactTracing;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class EventsConfig {
    @Autowired
    private ContactTracing tracing =
        new ContactTracing();

    @Bean(name = "eventsExecutor")
    public Executor eventsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);

        executor.setQueueCapacity(200);
    
        executor.setThreadNamePrefix("events-");
        // Copy only the technical correlation ID, never arbitrary caller context.
        executor.setTaskDecorator(task -> {
            String captured = org.slf4j.MDC.get("requestId");
            Runnable traced = tracing.decorate(task);
            return () -> {
                String previous = org.slf4j.MDC.get("requestId");
                try {
                    if (captured == null) org.slf4j.MDC.remove("requestId");
                    else org.slf4j.MDC.put("requestId", captured);
                    traced.run();
                } finally {
                    if (previous == null) org.slf4j.MDC.remove("requestId");
                    else org.slf4j.MDC.put("requestId", previous);
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
