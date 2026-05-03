package com.grabmyseat.gateway.security;

import com.grabmyseat.gateway.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtValidator {

    private final SecretKey key;

    public JwtValidator(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    // throws JwtException on bad signature, expiry or malformed token
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccess(Claims claims) {
        return "access".equals(claims.get("type"));
    }

    @SuppressWarnings("unchecked")
    public List<String> roles(Claims claims) {
        Object raw = claims.get("roles");
        return raw == null ? List.of() : (List<String>) raw;
    }
}
