package com.raullopezpenalva.contact_service.contact.application.model;

public class ClientContext {
    
    private String sourceIP;
    private String userAgent;

    public ClientContext(String sourceIP, String userAgent) {
        this.sourceIP = sourceIP;
        this.userAgent = userAgent;
    }

    public String getSourceIP() {
        return sourceIP;
    }

    public void setSourceIP(String sourceIP) {
        this.sourceIP = sourceIP;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
