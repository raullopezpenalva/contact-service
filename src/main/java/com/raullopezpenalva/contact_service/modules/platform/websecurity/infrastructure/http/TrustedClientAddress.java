package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import jakarta.servlet.http.HttpServletRequest;

public final class TrustedClientAddress {
    private TrustedClientAddress() {}

    public static String resolve(HttpServletRequest request, String trustedPeers) {
        String peer = literal(request.getRemoteAddr());
        boolean trusted = peer != null && Arrays.stream(trustedPeers.split(","))
            .map(String::trim).map(TrustedClientAddress::literal).anyMatch(peer::equals);
        var headers = Collections.list(request.getHeaders("X-Real-IP"));
        String client = trusted && headers.size() == 1 ? literal(headers.getFirst()) : null;
        return client != null ? client : peer != null ? peer : request.getRemoteAddr();
    }

    // Only numeric literals reach InetAddress; this path never resolves a hostname.
    private static String literal(String value) {
        if (value == null || value.length() > 45 || !value.matches("[0-9a-fA-F:.]+")) return null;
        if (!value.contains(":")) {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) return null;
            for (String part : parts) {
                if (!part.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(part) > 255) return null;
            }
        }
        try { return InetAddress.getByName(value).getHostAddress(); }
        catch (UnknownHostException ex) { return null; }
    }
}
