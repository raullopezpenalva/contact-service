package com.raullopezpenalva.contact_service.modules.platform.websecurity.domain;

import java.time.Instant;
import java.util.*;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name="admin_accounts")
public class AdminAccount {
    @Id private UUID id;
    @Column(nullable=false, unique=true, length=64) private String login;
    @Column(name="password_hash", nullable=false, length=255) private String passwordHash;
    @Column(nullable=false) private boolean enabled;
    @Column(name="authentication_version", nullable=false) private long authenticationVersion;
    @Version @Column(name="row_version", nullable=false) private long rowVersion;
    @ElementCollection(fetch=FetchType.EAGER)
    @CollectionTable(name="admin_account_roles",joinColumns=@JoinColumn(name="account_id"))
    @Enumerated(EnumType.STRING) @Column(name="role",nullable=false,length=32)
    private Set<AdminRole> roles=new HashSet<>();
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;

    protected AdminAccount() {}
    public static String canonicalLogin(String input) {
        if (input==null) throw new IllegalArgumentException("Invalid login identifier");
        String login=input.strip().toLowerCase(Locale.ROOT);
        if (!login.matches("[a-z0-9][a-z0-9._-]{2,63}")) throw new IllegalArgumentException("Invalid login identifier");
        return login;
    }
    public static AdminAccount create(String login, String hash) {
        var account=new AdminAccount();
        account.id=UUID.randomUUID(); account.login=canonicalLogin(login); account.passwordHash=hash;
        account.enabled=true; account.roles.add(AdminRole.ADMIN);
        account.createdAt=Instant.now(); account.updatedAt=account.createdAt;
        return account;
    }
    public void resetPassword(String hash) {passwordHash=hash; invalidateAuthentication();}
    public void setEnabled(boolean value) {if(enabled!=value){enabled=value;invalidateAuthentication();}}
    public void setRoles(Set<AdminRole> value) {if(!roles.equals(value)){roles=new HashSet<>(value);invalidateAuthentication();}}
    public void invalidateAuthentication() {authenticationVersion++;updatedAt=Instant.now();}
    public UUID getId(){return id;}
    public String getLogin(){return login;}
    @JsonIgnore public String getPasswordHash(){return passwordHash;}
    public boolean isEnabled(){return enabled;}
    public Set<AdminRole> getRoles(){return Set.copyOf(roles);}
    public long getAuthenticationVersion(){return authenticationVersion;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getUpdatedAt(){return updatedAt;}
}
