package com.raullopezpenalva.contact_service.modules.platform.websecurity.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="admin_sessions")
public class AdminSession {
    @Id private UUID id;
    @Column(name="account_id",nullable=false) private UUID accountId;
    @Column(name="authentication_version",nullable=false) private long authenticationVersion;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="expires_at",nullable=false) private Instant expiresAt;
    @Column(name="revoked_at") private Instant revokedAt;
    @Column(name="signing_key_id",length=64) private String signingKeyId;
    @Column(name="token_id") private UUID tokenId;
    protected AdminSession() {}
    public AdminSession(UUID accountId,long version,Instant now,String keyId) {
        id=UUID.randomUUID();tokenId=UUID.randomUUID();this.accountId=accountId;
        authenticationVersion=version;createdAt=now;expiresAt=now.plusSeconds(600);signingKeyId=keyId;
    }
    public UUID getId(){return id;}
    public UUID getAccountId(){return accountId;}
    public long getAuthenticationVersion(){return authenticationVersion;}
    public Instant getCreatedAt(){return createdAt;}
    public Instant getExpiresAt(){return expiresAt;}
    public Instant getRevokedAt(){return revokedAt;}
    public String getSigningKeyId(){return signingKeyId;}
    public UUID getTokenId(){return tokenId;}
    public void revoke(Instant now){if(revokedAt==null) revokedAt=now;}
}
