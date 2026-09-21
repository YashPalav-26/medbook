package com.medbook.security;

import com.medbook.entity.User;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Creates the signed JWTs that clients present as {@code Authorization: Bearer ...}.
 *
 * <p>Claims are deliberately small and non-sensitive:</p>
 * <ul>
 * <li>{@code iss} — {@code "medbook"}, identifies who issued the token,</li>
 * <li>{@code sub} — the user ID (the stable primary key, not an editable field),</li>
 * <li>{@code email} — the sign-in identity,</li>
 * <li>{@code roles} — Spring Security authorities with the {@code ROLE_} prefix
 *     (for example {@code "ROLE_PATIENT"}),</li>
 * <li>{@code iat} / {@code exp} — issued-at and expiration; the lifetime comes from
 *     configuration ({@code JWT_EXPIRATION_MINUTES}, default 60).</li>
 * </ul>
 *
 * <p>A JWT is only base64-encoded, <strong>not encrypted</strong>: never put the password
 * hash, contact details, or anything else sensitive into a claim.</p>
 *
 * <p>The signing key is HMAC (HS256) and comes from configuration; it is never embedded in
 * source code or committed files.</p>
 */
@Service
public class JwtService {

    /** Name of the JWT claim carrying the user's Spring Security authorities. */
    public static final String ROLES_CLAIM = "roles";

    /** Identifies tokens issued by this backend. */
    public static final String ISSUER = "medbook";

    private final JwtEncoder jwtEncoder;
    private final long expirationMinutes;

    public JwtService(JwtEncoder jwtEncoder, JwtProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.expirationMinutes = properties.getExpirationMinutes();
    }

    /**
     * Issue a signed token for an authenticated user.
     *
     * @param user the persisted user the token represents
     * @return the compact JWT string (header.payload.signature)
     */
    public String generateToken(User user) {
        Instant now = Instant.now();

        List<String> roles = user.getRoles().stream()
                .map(role -> "ROLE_" + role.getName())
                .sorted()
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(expirationMinutes, ChronoUnit.MINUTES))
                .claim("email", user.getEmail())
                .claim(ROLES_CLAIM, roles)
                .build();

        JwsHeader headers = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }
}
