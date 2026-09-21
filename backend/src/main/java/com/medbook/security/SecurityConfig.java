package com.medbook.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Central Spring Security configuration.
 *
 * <h3>Beans</h3>
 * <ul>
 * <li>{@link PasswordEncoder} — BCrypt for hashing user passwords,</li>
 * <li>JWT signing key — HMAC key derived from the configured secret (or an ephemeral
 *     development key with a loud warning when nothing is configured),</li>
 * <li>{@link JwtEncoder} / {@link JwtDecoder} — Nimbus JOSE signing and validation,</li>
 * <li>{@link SecurityFilterChain} — the actual request rules.</li>
 * </ul>
 *
 * <h3>Filter-chain decisions</h3>
 * <ul>
 * <li><strong>Stateless:</strong> no HTTP session is created; every request must carry its
 *     own Bearer token.</li>
 * <li><strong>CSRF disabled:</strong> CSRF attacks rely on browsers automatically attaching
 *     cookies. This API never uses cookie/session authentication, so there is nothing to
 *     protect with CSRF tokens. If cookie-based auth is ever added, CSRF must be re-enabled.</li>
 * <li><strong>formLogin / httpBasic / logout disabled:</strong> this is a token API, not a
 *     browser-session application.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Shortest acceptable length for a configured JWT secret. */
    private static final int MIN_SECRET_LENGTH = 32;

    /** Key algorithm for the HMAC signing key. */
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * BCrypt password hashing. The output is a self-contained modular string
     * ({@code $2a$10$...}) that embeds the algorithm cost and a random salt, so two users
     * with the same password never share a hash.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Build the HMAC signing key for JWTs.
     *
     * <p>The secret material comes only from configuration/environment
     * ({@code medbook.security.jwt.secret} or {@code JWT_SECRET}). When nothing is
     * configured, a random key is generated for local development and a warning is logged —
     * tokens simply do not survive a restart in that mode. A configured secret shorter than
     * {@value #MIN_SECRET_LENGTH} characters is rejected at startup.</p>
     */
    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) throws NoSuchAlgorithmException {
        String secret = properties.getSecret();

        if (secret == null || secret.isBlank()) {
            log.warn("No JWT secret configured (medbook.security.jwt.secret / JWT_SECRET). "
                    + "Generating an ephemeral development secret: tokens become invalid on "
                    + "restart. Set JWT_SECRET for stable tokens.");
            byte[] randomBytes = new byte[32];
            new SecureRandom().nextBytes(randomBytes);
            return new SecretKeySpec(randomBytes, HMAC_ALGORITHM);
        }

        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException("The configured JWT secret must be at least "
                    + MIN_SECRET_LENGTH + " characters long");
        }

        // Derive a fixed 256-bit HMAC key from the configured secret material.
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Translate the {@code roles} claim of a JWT into Spring Security authorities.
     *
     * <p>The claim already stores full authority names ({@code "ROLE_PATIENT"}), so the
     * default {@code SCOPE_} prefix is replaced with an empty prefix.</p>
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(JwtService.ROLES_CLAIM);
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }

    /**
     * The request rules for the whole application.
     *
     * <h3>Public endpoints</h3>
     * <ul>
     * <li>{@code /api/v1/auth/**} — registration and login (they are how a client obtains
     *     a token),</li>
     * <li>{@code /api/v1/hello} — the development smoke-test endpoint,</li>
     * <li>{@code /actuator/health}, {@code /actuator/info} — monitoring probes.</li>
     * </ul>
     *
     * <h3>Protected endpoints</h3>
     * <ul>
     * <li>{@code /api/v1/appointments/**} — requires a valid token with at least one of the
     *     business roles (ADMIN, DOCTOR, PATIENT). Ownership rules ("a patient may only see
     *     their own appointments") are deliberately not invented here; they arrive with the
     *     step that implements them in the service layer.</li>
     * <li>everything else requires an authenticated caller.</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            MedbookAuthenticationEntryPoint authenticationEntryPoint,
            MedbookAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/hello").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/appointments/**")
                                .hasAnyRole("ADMIN", "DOCTOR", "PATIENT")
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}
