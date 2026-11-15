package com.raullopezpenalva.contact_service.shared.events;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class EventsConfigTest {
    @Test void propagatesOnlyRequestIdAndCleansWorkerContext() throws Exception {
        var executor = (ThreadPoolTaskExecutor) new EventsConfig().eventsExecutor();
        executor.setCorePoolSize(1);
        try {
            MDC.put("requestId", "0123456789abcdef0123456789abcdef");
            MDC.put("privateContext", "must-not-propagate");
            var observed = new CompletableFuture<String>();
            executor.execute(() -> {
                assertNull(MDC.get("privateContext"));
                observed.complete(MDC.get("requestId"));
            });
            assertEquals(MDC.get("requestId"), observed.get(5, TimeUnit.SECONDS));
            var failed = executor.submit(() -> { throw new IllegalStateException("synthetic failure"); });
            assertThrows(java.util.concurrent.ExecutionException.class, () -> failed.get(5, TimeUnit.SECONDS));
            MDC.clear();
            assertNull(executor.getThreadPoolExecutor().submit(() -> MDC.get("requestId")).get(5, TimeUnit.SECONDS));
            var noContext = new CompletableFuture<Boolean>();
            executor.execute(() -> noContext.complete(MDC.get("requestId") == null));
            assertTrue(noContext.get(5, TimeUnit.SECONDS));
        } finally { MDC.clear(); executor.shutdown(); }
    }
}
