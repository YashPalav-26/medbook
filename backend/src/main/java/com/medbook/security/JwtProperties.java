package com.medbook.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


















@Component
public class JwtProperties {





    private final String secret;


    private final long expirationMinutes;

    public JwtProperties(
            @Value("${medbook.security.jwt.secret:${JWT_SECRET:}}") String secret,
            @Value("${medbook.security.jwt.expiration-minutes:${JWT_EXPIRATION_MINUTES:60}}") long expirationMinutes
    ) {
        this.secret = secret;
        this.expirationMinutes = expirationMinutes;
    }

    public String getSecret() {
        return secret;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }
}
