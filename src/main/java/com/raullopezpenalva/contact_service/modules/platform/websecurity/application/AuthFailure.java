package com.raullopezpenalva.contact_service.modules.platform.websecurity.application;

/** Only fixed server-owned messages belong here. */
public class AuthFailure extends RuntimeException {
    public final int status;
    public final int retryAfter;
    public AuthFailure(int status,String message,int retryAfter){super(message);this.status=status;this.retryAfter=retryAfter;}
}
