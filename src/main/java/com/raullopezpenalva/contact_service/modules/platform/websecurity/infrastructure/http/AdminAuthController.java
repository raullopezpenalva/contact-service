package com.raullopezpenalva.contact_service.modules.platform.websecurity.infrastructure.http;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.application.*;
import com.raullopezpenalva.contact_service.modules.platform.websecurity.domain.AdminRole;

@RestController
@RequestMapping("/api/v1/auth")
public class AdminAuthController {
    private final AdminLoginService login;
    private final AdminSessionService sessions;
    public AdminAuthController(AdminLoginService login,AdminSessionService sessions){this.login=login;this.sessions=sessions;}
    @io.swagger.v3.oas.annotations.media.Schema(requiredProperties={"username","password"})
    public static final class LoginInput {
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=AuthInputDeserializers.Username.class) public String username;
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=AuthInputDeserializers.Password.class)
        @io.swagger.v3.oas.annotations.media.Schema(type="string",format="password",accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY)
        @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) public char[] password;
    }
    @io.swagger.v3.oas.annotations.media.Schema(requiredProperties={"currentPassword","newPassword"})
    public static final class PasswordInput {
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=AuthInputDeserializers.Password.class)
        @io.swagger.v3.oas.annotations.media.Schema(type="string",format="password",accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY)
        @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) public char[] currentPassword;
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=AuthInputDeserializers.Password.class)
        @io.swagger.v3.oas.annotations.media.Schema(type="string",format="password",accessMode=io.swagger.v3.oas.annotations.media.Schema.AccessMode.WRITE_ONLY)
        @JsonProperty(access=JsonProperty.Access.WRITE_ONLY) public char[] newPassword;
    }
    public record Token(@JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,@JsonProperty("expires_in") int expiresIn) {
        @Override public String toString(){return "Admin access token response";}
    }
    public record Me(UUID id,String login,Set<AdminRole> roles) {}
    @PostMapping(value="/login",consumes=MediaType.APPLICATION_JSON_VALUE)
    public Token login(@RequestBody LoginInput body) {
        try {
            if(body.username==null || body.username.length()>128) throw new IllegalArgumentException();
            // Syntactic input failures differ from valid but unknown accounts.
            com.raullopezpenalva.contact_service.modules.platform.websecurity.domain.AdminAccount.canonicalLogin(body.username);
            return new Token(login.login(body.username,body.password),"Bearer",600);
        } finally {clear(body.password);}
    }
    @GetMapping("/me") public Me me(@AuthenticationPrincipal Jwt jwt) {
        var account=sessions.me(jwt);return new Me(account.id(),account.login(),account.roles());
    }
    @PostMapping("/logout") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt){sessions.logout(jwt);}
    @PostMapping(value="/change-password",consumes=MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void password(@AuthenticationPrincipal Jwt jwt,@RequestBody PasswordInput body) {
        try {login.changePassword(jwt,body.currentPassword,body.newPassword);}
        finally {clear(body.currentPassword);clear(body.newPassword);}
    }
    private static void clear(char[] value){if(value!=null)Arrays.fill(value,'\0');}
    @ExceptionHandler(AuthenticationException.class)
    public void invalid(HttpServletRequest request,HttpServletResponse response) throws java.io.IOException {
        response.setHeader("WWW-Authenticate","Bearer");SecurityErrorWriter.write(request,response,401,"Invalid credentials");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public void malformed(HttpServletRequest request,HttpServletResponse response) throws java.io.IOException {
        SecurityErrorWriter.write(request,response,400,"Invalid request");
    }
    @ExceptionHandler(AuthFailure.class)
    public void limited(AuthFailure failure,HttpServletRequest request,HttpServletResponse response) throws java.io.IOException {
        response.setHeader("Retry-After",Integer.toString(failure.retryAfter));
        SecurityErrorWriter.write(request,response,failure.status,failure.getMessage());
    }
    @ExceptionHandler(DataAccessException.class)
    public void unavailable(HttpServletRequest request,HttpServletResponse response) throws java.io.IOException {
        SecurityErrorWriter.write(request,response,503,"Service unavailable");
    }
}
