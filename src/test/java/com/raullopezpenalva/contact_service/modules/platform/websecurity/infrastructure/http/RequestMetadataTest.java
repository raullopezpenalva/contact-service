package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;

class RequestMetadataTest {
    private static final String ID = "0123456789abcdef0123456789abcdef";
    private MockHttpServletRequest request(String peer, String... values) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(peer);
        for (String value : values) request.addHeader("X-Request-ID", value);
        return request;
    }
    @Test void acceptsOnlyOneGeneratedIdFromImmediateTrustedPeer() {
        var request = request("172.18.0.4", ID);
        RequestMetadata.initialize(request, "172.18.0.4");
        assertEquals(ID, RequestMetadata.requestId(request));
    }
    @Test void rejectsSpoofedMalformedAndDuplicateHeaders() {
        for (var request : new MockHttpServletRequest[]{
                request("203.0.113.10", ID), request("172.18.0.4", ID, ID),
                request("172.18.0.4", "secret-user-value"), request("172.18.0.4", ID.toUpperCase()),
                request("172.18.0.4", ID + "," + ID)}) {
            request.addHeader("X-Forwarded-For", "172.18.0.4");
            RequestMetadata.initialize(request, "172.18.0.4");
            String generated = RequestMetadata.requestId(request);
            assertNotEquals(ID, generated);
            assertDoesNotThrow(() -> java.util.UUID.fromString(generated));
            assertEquals(generated, RequestMetadata.requestId(request));
        }
    }
}
