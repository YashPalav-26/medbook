package com.medbook.security;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;















@Component
public class CurrentUserProvider {






    public Optional<Long> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication instanceof JwtAuthenticationToken jwtAuthentication
                && jwtAuthentication.isAuthenticated()) {
            String subject = jwtAuthentication.getToken().getSubject();
            if (subject != null) {
                try {
                    return Optional.of(Long.parseLong(subject));
                } catch (NumberFormatException ignored) {

                }
            }
        }
        return Optional.empty();
    }




    public Optional<String> currentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return Optional.ofNullable(jwtAuthentication.getToken().getClaimAsString("email"));
        }
        return Optional.empty();
    }










    public Long requireCurrentUserId() {
        return currentUserId().orElseThrow(() ->
                new InsufficientAuthenticationException(
                        "No authenticated user available in the security context"));
    }
}
