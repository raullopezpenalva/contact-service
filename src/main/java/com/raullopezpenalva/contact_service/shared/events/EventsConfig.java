package com.raullopezpenalva.contact_service.shared.events;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class EventsConfig {

    @Bean(name = "eventsExecutor")
    public Executor eventsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);

        executor.setQueueCapacity(200);
    
        executor.setThreadNamePrefix("events-");
        executor.initialize();
        return executor;
    }
}
