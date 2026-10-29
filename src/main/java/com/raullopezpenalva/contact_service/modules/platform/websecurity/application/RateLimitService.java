package com.raullopezpenalva.contact_service.modules.platform.websecurity.application;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RateLimitService {
    private final int maxRequests;
    private final long windowSeconds;
    private final ConcurrentHashMap<String, ClientRateLimitState> clients;

    public RateLimitService(
        @Value("${app.security.rate-limit.contactrequest.max-requests:5}") int maxRequests,
        @Value("${app.security.rate-limit.contactrequest.window-seconds:600}") long windowSeconds
    ) {
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
        this.clients = new ConcurrentHashMap<>();
    }

    public boolean allowRequest(String clientKey) {
        Instant now = Instant.now();

        ClientRateLimitState state = clients.compute(clientKey, (key, currentState ) -> {
            if (currentState == null || hasWindowExpired(currentState, now)) {
                return new ClientRateLimitState(1, now);
            } else {
                currentState.incrementRequestCount();
                return currentState;
            }
        });
        return state.getRequestCount() <= maxRequests;
    }

    private boolean hasWindowExpired(ClientRateLimitState state, Instant now) {
        return now.isAfter(state.getWindowStart().plusSeconds(windowSeconds));
    }

    private static final class ClientRateLimitState {
        private int requestCount;
        private final Instant windowStart;

        public ClientRateLimitState(int requestCount, Instant windowStart) {
            this.requestCount = requestCount;
            this.windowStart = windowStart;
        }

        public int getRequestCount() {
            return requestCount;
        }

        public Instant getWindowStart() {
            return windowStart;
        }

        public void incrementRequestCount() {
            this.requestCount++;
        }
    }
}
