package com.raullopezpenalva.contact_service.modules.platform.websecurity.application;

import java.util.concurrent.Semaphore;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.jwt.ManagedAdminPrincipal;

@Service
public class AdminLoginService {
    private final AuthenticationManager authentication;
    private final AdminSessionService sessions;
    private final Semaphore hashing=new Semaphore(2);
    public AdminLoginService(AuthenticationManager authentication,AdminSessionService sessions) {
        this.authentication=authentication;this.sessions=sessions;
    }
    private ManagedAdminPrincipal verify(String login,char[] password) {
        AdminPasswordHasher.validate(password);
        var token=UsernamePasswordAuthenticationToken.unauthenticated(login,java.nio.CharBuffer.wrap(password));
        try {
            Authentication result=authentication.authenticate(token);
            return (ManagedAdminPrincipal)result.getPrincipal();
        } finally {token.eraseCredentials();}
    }
    public String login(String login,char[] password) {
        if(!hashing.tryAcquire()) throw new AuthFailure(429,"Too many requests",1);
        try {var principal=verify(login,password);return sessions.issue(principal.id,principal.version);}
        finally {hashing.release();}
    }
    public void changePassword(Jwt jwt,char[] current,char[] replacement) {
        AdminPasswordHasher.validate(replacement);
        if(!hashing.tryAcquire()) throw new AuthFailure(429,"Too many requests",1);
        try {
            var me=sessions.me(jwt);
            var principal=verify(me.login(),current);
            if(!principal.id.toString().equals(jwt.getSubject())) throw new BadCredentialsException("Invalid credentials");
            sessions.changePassword(jwt,principal.version,replacement);
        } finally {hashing.release();}
    }
}
