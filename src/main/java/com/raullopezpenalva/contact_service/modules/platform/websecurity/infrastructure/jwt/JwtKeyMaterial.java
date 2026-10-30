package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.jwt;

import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
import org.springframework.security.converter.RsaKeyConverters;
import com.nimbusds.jose.jwk.*;

/** Fixed, local key allowlist. Never resolves token-supplied URLs or creates keys. */
public final class JwtKeyMaterial {
    public final String issuer;
    public final String origin;
    public final String activeId;
    private final RSAKey signer;
    private final JWKSet verification;

    public JwtKeyMaterial(String issuer, String activeId, Path privateFile, Path publicDirectory) {
        try {
            URI uri=URI.create(issuer);
            if (!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getRawUserInfo()!=null
                    || uri.getRawQuery()!=null || uri.getRawFragment()!=null || !issuer.equals(uri.toASCIIString())
                    || !activeId.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException();
            this.issuer=issuer; this.activeId=activeId;
            this.origin=new URI("https",null,uri.getHost(),uri.getPort(),null,null,null).toString();
            checkFile(privateFile);
            Set<PosixFilePermission> permissions=Files.getPosixFilePermissions(privateFile);
            if (!permissions.contains(PosixFilePermission.OWNER_READ) || permissions.stream().anyMatch(p->p!=PosixFilePermission.OWNER_READ && p!=PosixFilePermission.OWNER_WRITE))
                throw new IllegalArgumentException();
            RSAPrivateKey privateKey;
            try(var stream=Files.newInputStream(privateFile,LinkOption.NOFOLLOW_LINKS)) {privateKey=RsaKeyConverters.pkcs8().convert(stream);}
            if(privateKey.getModulus().bitLength()<3072 || privateKey.getModulus().bitLength()>8192) throw new IllegalArgumentException();
            if(Files.isSymbolicLink(publicDirectory) || !Files.isDirectory(publicDirectory)) throw new IllegalArgumentException();
            List<JWK> keys=new ArrayList<>(); RSAPublicKey active=null;
            try(var files=Files.list(publicDirectory)) {
                for(Path file:files.limit(9).toList()) {
                    if(keys.size()>=8) throw new IllegalArgumentException();
                    String name=file.getFileName().toString();
                    if(!name.matches("[A-Za-z0-9_-]{1,64}\\.pub\\.pem")) throw new IllegalArgumentException();
                    checkFile(file); RSAPublicKey key;
                    try(var stream=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {key=RsaKeyConverters.x509().convert(stream);}
                    if(key.getModulus().bitLength()<3072 || key.getModulus().bitLength()>8192) throw new IllegalArgumentException();
                    String id=name.substring(0,name.length()-8);
                    keys.add(new RSAKey.Builder(key).keyID(id).build());
                    if(id.equals(activeId)) active=key;
                }
            }
            if(active==null || !active.getModulus().equals(privateKey.getModulus())) throw new IllegalArgumentException();
            // Verify the pair using the standard JCA provider; matching modulus alone is insufficient.
            byte[] challenge=new byte[32];new SecureRandom().nextBytes(challenge);
            Signature signature=Signature.getInstance("SHA256withRSA");signature.initSign(privateKey);signature.update(challenge);
            byte[] signed=signature.sign();signature.initVerify(active);signature.update(challenge);
            if(!signature.verify(signed)) throw new IllegalArgumentException();
            signer=new RSAKey.Builder(active).privateKey(privateKey).keyID(activeId).build();
            verification=new JWKSet(keys);
        } catch(Exception failure) {
            // Key contents, locations and parser causes must never reach startup logs.
            throw new IllegalStateException("Invalid admin JWT signing configuration");
        }
    }
    private static void checkFile(Path file) throws Exception {
        if(Files.isSymbolicLink(file) || !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)
                || Files.size(file)<64 || Files.size(file)>16384) throw new IllegalArgumentException();
    }
    public JWKSet signingKeys(){return new JWKSet(signer);}
    public JWKSet verificationKeys(){return verification;}
}
