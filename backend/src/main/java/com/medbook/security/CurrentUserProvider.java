package com.medbook.security;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Clean access to the currently authenticated user for application code.
 *
 * <p>After the JWT filter accepts a token, Spring Security stores a
 * {@link JwtAuthenticationToken} in the {@link SecurityContextHolder}. This provider reads
 * it, so services never have to trust a user ID sent in a request body.</p>
 *
 * <h3>Where it is used</h3>
 * <p>{@code AppointmentService} resolves the real caller through this provider and records
 * them in {@code appointment_status_history.changed_by_user_id} for every status change
 * (creation, cancellation, confirmation, completion, no-show). A {@code NULL} actor now
 * only occurs for context-free internal/test calls that legitimately run without
 * authentication.</p>
 */
@Component
public class CurrentUserProvider {

    /**
     * The ID of the authenticated user (the JWT {@code sub} claim), if there is one.
     *
     * @return the user ID, or empty when the current thread has no authenticated JWT caller
     */
    public Optional<Long> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication
                && jwtAuthentication.isAuthenticated()) {
            String subject = jwtAuthentication.getToken().getSubject();
            if (subject != null) {
                try {
                    return Optional.of(Long.parseLong(subject));
                } catch (NumberFormatException ignored) {
                    // Tokens issued by this backend always carry a numeric subject.
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The email of the authenticated user (the JWT {@code email} claim), if present.
     */
    public Optional<String> currentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return Optional.ofNullable(jwtAuthentication.getToken().getClaimAsString("email"));
        }
        return Optional.empty();
    }

    /**
     * The ID of the authenticated user, or an exception when nobody is authenticated.
     *
     * <p>Intended for service methods that must never run anonymously — for example a future
     * "cancel my appointment" that writes {@code changed_by_user_id}.</p>
     *
     * @return the authenticated user's ID
     * @throws InsufficientAuthenticationException when there is no authenticated caller
     */
    public Long requireCurrentUserId() {
        return currentUserId().orElseThrow(() ->
                new InsufficientAuthenticationException(
                        "No authenticated user available in the security context"));
    }
}
