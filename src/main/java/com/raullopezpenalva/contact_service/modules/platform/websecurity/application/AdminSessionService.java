package com.raullopezpenalva.contact_service.modules.platform.websecurity.application;

import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.domain.*;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.persistence.*;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.jwt.JwtKeyMaterial;

@Service
public class AdminSessionService {
    private final AdminAccountRepository accounts;
    private final AdminSessionRepository sessions;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtKeyMaterial keys;
    private final AdminPasswordHasher passwords;
    public AdminSessionService(AdminAccountRepository accounts,AdminSessionRepository sessions,JdbcTemplate jdbc,
            Clock clock,JwtEncoder encoder,JwtKeyMaterial keys,AdminPasswordHasher passwords) {
        this.accounts=accounts;this.sessions=sessions;this.jdbc=jdbc;this.clock=clock;
        this.encoder=encoder;this.keys=keys;this.passwords=passwords;
    }
    private void lock(){jdbc.queryForObject("SELECT id FROM admin_management_lock WHERE id=1 FOR UPDATE",Integer.class);}
    private static BadCredentialsException invalid(){return new BadCredentialsException("Invalid credentials");}

    @Transactional
    public String issue(UUID id,long authenticatedVersion) {
        lock(); var account=accounts.findById(id).orElseThrow(AdminSessionService::invalid);
        if(!account.isEnabled() || !account.getRoles().contains(AdminRole.ADMIN)
                || account.getAuthenticationVersion()!=authenticatedVersion) throw invalid();
        Instant now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        // Bounded housekeeping is not part of authorization correctness.
        var expired=sessions.findByExpiresAtBeforeOrderByExpiresAtAsc(now,PageRequest.of(0,100));
        sessions.deleteAllInBatch(expired);
        if(sessions.countByAccountIdAndRevokedAtIsNullAndExpiresAtAfter(id,now)>=10)
            throw new AuthFailure(429,"Too many active sessions",60);
        var session=sessions.saveAndFlush(new AdminSession(id,authenticatedVersion,now,keys.activeId));
        var claims=JwtClaimsSet.builder().issuer(keys.issuer).audience(List.of("contact-service-admin"))
            .subject(id.toString()).id(session.getTokenId().toString()).issuedAt(now).notBefore(now)
            .expiresAt(session.getExpiresAt()).claim("sid",session.getId().toString())
            .claim("roles",account.getRoles().stream().map(Enum::name).sorted().toList())
            .claim("authenticationVersion",authenticatedVersion).build();
        var header=JwsHeader.with(SignatureAlgorithm.RS256).type("at+jwt").keyId(keys.activeId).build();
        return encoder.encode(JwtEncoderParameters.from(header,claims)).getTokenValue();
    }

    /** No authentication cache: live account and session are read on every request. */
    @Transactional(readOnly=true)
    public Set<AdminRole> effectiveRoles(Jwt jwt) {
        var session=sessions.findById(UUID.fromString(jwt.getClaimAsString("sid"))).orElseThrow(AdminSessionService::invalid);
        var account=accounts.findById(UUID.fromString(jwt.getSubject())).orElseThrow(AdminSessionService::invalid);
        Number version=jwt.getClaim("authenticationVersion");
        if(!account.isEnabled() || session.getRevokedAt()!=null || !session.getExpiresAt().isAfter(clock.instant())
                || !session.getAccountId().equals(account.getId())
                || account.getAuthenticationVersion()!=version.longValue()
                || session.getAuthenticationVersion()!=version.longValue()
                || !Objects.equals(session.getSigningKeyId(),jwt.getHeaders().get("kid"))
                || !Objects.equals(session.getTokenId().toString(),jwt.getId())
                || !session.getCreatedAt().equals(jwt.getIssuedAt())
                || !session.getExpiresAt().equals(jwt.getExpiresAt())) throw invalid();
        Set<AdminRole> roles=new HashSet<>(account.getRoles());
        roles.removeIf(role->!jwt.getClaimAsStringList("roles").contains(role.name()));
        return Set.copyOf(roles);
    }
    @Transactional
    public void logout(Jwt jwt) {
        lock(); effectiveRoles(jwt);
        sessions.findById(UUID.fromString(jwt.getClaimAsString("sid"))).orElseThrow(AdminSessionService::invalid).revoke(clock.instant());
    }
    @Transactional
    public void changePassword(Jwt jwt,long verifiedVersion,char[] password) {
        AdminPasswordHasher.validate(password); lock(); effectiveRoles(jwt);
        var account=accounts.findById(UUID.fromString(jwt.getSubject())).orElseThrow(AdminSessionService::invalid);
        if(account.getAuthenticationVersion()!=verifiedVersion || !account.getRoles().contains(AdminRole.ADMIN)) throw invalid();
        account.resetPassword(passwords.encode(password));accounts.flush();
        jdbc.update("UPDATE admin_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE account_id=? AND revoked_at IS NULL",account.getId());
    }
    @Transactional(readOnly=true)
    public AdminAccountService.Summary me(Jwt jwt) {
        Set<AdminRole> roles=effectiveRoles(jwt);
        var a=accounts.findById(UUID.fromString(jwt.getSubject())).orElseThrow(AdminSessionService::invalid);
        return new AdminAccountService.Summary(a.getId(),a.getLogin(),a.isEnabled(),roles,a.getAuthenticationVersion());
    }
}
