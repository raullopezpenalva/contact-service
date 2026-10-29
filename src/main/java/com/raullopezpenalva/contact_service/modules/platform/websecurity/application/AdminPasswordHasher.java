package com.raullopezpenalva.contact_service.modules.platform.websecurity.application;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Not a global PasswordEncoder bean: legacy Basic remains separate until #48. */
@Component
public class AdminPasswordHasher {
    private final PasswordEncoder encoder=new Argon2PasswordEncoder(16,32,1,19456,2);
    private final String dummyHash=encoder.encode(UUID.randomUUID().toString());

    public String encode(char[] password) {validate(password); return encoder.encode(CharBuffer.wrap(password));}
    public boolean matches(char[] password,String hash) {
        if(password==null || password.length>256) return false;
        // Bound decoded work factors and avoid passing corrupt hashes to provider diagnostics.
        boolean supported=hash!=null && hash.matches("\\$argon2id\\$v=19\\$m=19456,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}");
        return encoder.matches(CharBuffer.wrap(password),supported?hash:dummyHash) && supported;
    }
    public boolean eligiblePasswordMatches(char[] password,String hash,boolean eligible) {
        boolean matches=matches(password,eligible?hash:null);
        return eligible && matches;
    }
    public static void validate(char[] password) {
        if(password==null || password.length>256 || !StandardCharsets.UTF_8.newEncoder().canEncode(CharBuffer.wrap(password)))
            throw new IllegalArgumentException("Invalid password length or encoding");
        int length=Character.codePointCount(password,0,password.length);
        if(length<15 || length>128) throw new IllegalArgumentException("Password must contain 15 to 128 Unicode characters");
    }
}
