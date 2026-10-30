package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.jwt;

import java.util.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.domain.AdminAccount;

public final class ManagedAdminPrincipal implements UserDetails,CredentialsContainer {
    public final UUID id;
    public final long version;
    private final String login;
    private String hash;
    private final List<GrantedAuthority> authorities;
    public ManagedAdminPrincipal(AdminAccount account) {
        id=account.getId();version=account.getAuthenticationVersion();login=account.getLogin();hash=account.getPasswordHash();
        authorities=account.getRoles().stream().<GrantedAuthority>map(r->new SimpleGrantedAuthority("ROLE_"+r.name())).toList();
    }
    public String getUsername(){return login;}
    public String getPassword(){return hash;}
    public Collection<? extends GrantedAuthority> getAuthorities(){return authorities;}
    public void eraseCredentials(){hash=null;}
    @Override public String toString(){return "Managed administrator principal";}
}
