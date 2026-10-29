package com.raullopezpenalva.contact_service.modules.platform.websecurity.application;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Single-instance, bounded fixed-window limiter. */
@Service
public class RateLimitService {
    private final Clock clock;
    private final Map<String, Window> clients = new HashMap<>();
    private static final int MAX_CLIENTS = 10000;
    public RateLimitService() { this(Clock.systemUTC()); }
    public RateLimitService(Clock clock) { this.clock = clock; }

    public synchronized boolean allowRequest(String key, int limit, long seconds) {
        long now = clock.millis();
        clients.entrySet().removeIf(e -> e.getValue().expiresAt <= now);
        Window window = clients.get(key);
        if (window == null) {
            if (clients.size() >= MAX_CLIENTS) return false;
            window = new Window(now + seconds * 1000);
            clients.put(key, window);
        }
        if (window.count >= limit) return false;
        window.count++;
        return true;
    }
    private static class Window {
        final long expiresAt;
        int count;
        Window(long expiresAt) { this.expiresAt = expiresAt; }
    }
}
