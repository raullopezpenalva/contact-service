package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.jwt;

import java.nio.file.Path;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.application.*;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.domain.*;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.persistence.AdminAccountRepository;

@Configuration(proxyBeanMethods=false)
public class AdminJwtConfiguration {
    @Bean Clock adminClock(){return Clock.systemUTC();}
    @Bean JwtKeyMaterial adminKeys(@Value("${app.security.jwt.issuer}") String issuer,
            @Value("${app.security.jwt.active-key-id}") String kid,
            @Value("${app.security.jwt.private-key-file}") String privateFile,
            @Value("${app.security.jwt.public-key-directory}") String directory) {
        return new JwtKeyMaterial(issuer,kid,Path.of(privateFile),Path.of(directory));
    }
    @Bean public JwtEncoder jwtEncoder(JwtKeyMaterial keys){return new NimbusJwtEncoder(new ImmutableJWKSet<>(keys.signingKeys()));}
    @Bean public JwtDecoder jwtDecoder(JwtKeyMaterial keys,Clock clock) {
        var processor=new DefaultJWTProcessor<SecurityContext>();
        processor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("at+jwt")));
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256,new ImmutableJWKSet<>(keys.verificationKeys())));
        // Spring below owns required claims and the injectable time policy.
        processor.setJWTClaimsSetVerifier((claims,context)->{});
        var decoder=new NimbusJwtDecoder(processor);
        decoder.setJwtValidator(jwt->validateClaims(jwt,keys,clock));
        return decoder;
    }
    public static OAuth2TokenValidatorResult validateClaims(Jwt jwt,JwtKeyMaterial keys,Clock clock) {
        try {
            if(!jwt.getHeaders().keySet().equals(Set.of("alg","typ","kid"))
                    || !"RS256".equals(jwt.getHeaders().get("alg")) || !"at+jwt".equals(jwt.getHeaders().get("typ"))
                    || !(jwt.getHeaders().get("kid") instanceof String kid) || !kid.matches("[A-Za-z0-9_-]{1,64}")
                    || !keys.issuer.equals(jwt.getClaimAsString("iss"))
                    || !List.of("contact-service-admin").equals(jwt.getAudience())) throw new IllegalArgumentException();
            for(String claim:List.of("sub","sid","jti")) {
                String value=jwt.getClaimAsString(claim);
                if(!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException();
            }
            Object version=jwt.getClaim("authenticationVersion");
            if(!(version instanceof Long || version instanceof Integer) || ((Number)version).longValue()<0) throw new IllegalArgumentException();
            Object rawRoles=jwt.getClaim("roles");
            if(!(rawRoles instanceof List<?> roles) || roles.size()>1 || roles.stream().anyMatch(r->!"ADMIN".equals(r)))
                throw new IllegalArgumentException();
            Instant issued=jwt.getIssuedAt(),notBefore=jwt.getNotBefore(),expires=jwt.getExpiresAt(),now=clock.instant();
            if(issued==null || notBefore==null || expires==null || !notBefore.equals(issued)
                    || !expires.equals(issued.plusSeconds(600)) || issued.isAfter(now.plusSeconds(30))
                    || !expires.isAfter(now.minusSeconds(30))) throw new IllegalArgumentException();
            return OAuth2TokenValidatorResult.success();
        } catch(Exception invalid) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Invalid access token",null));
        }
    }
    @Bean AuthenticationManager adminAuthenticationManager(AdminAccountRepository accounts,AdminPasswordHasher hasher,
            ApplicationEventPublisher events) {
        UserDetailsService users=login->{
            try {
                var a=accounts.findByLogin(AdminAccount.canonicalLogin(login)).orElseThrow(()->new UsernameNotFoundException("Invalid credentials"));
                if(!a.isEnabled() || !a.getRoles().contains(AdminRole.ADMIN)) throw new UsernameNotFoundException("Invalid credentials");
                return new ManagedAdminPrincipal(a);
            } catch(IllegalArgumentException invalid){throw new UsernameNotFoundException("Invalid credentials");}
        };
        var provider=new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(new PasswordEncoder() {
            public String encode(CharSequence raw){
                char[] value=raw.toString().toCharArray();
                try{return hasher.encode(value);}finally{Arrays.fill(value,'\0');}
            }
            public boolean matches(CharSequence raw,String hash){
                char[] value=raw==null?null:raw.toString().toCharArray();
                try{return hasher.matches(value,hash);}finally{if(value!=null)Arrays.fill(value,'\0');}
            }
        });
        var manager=new ProviderManager(provider);
        manager.setAuthenticationEventPublisher(new DefaultAuthenticationEventPublisher(events));
        return manager;
    }
}
