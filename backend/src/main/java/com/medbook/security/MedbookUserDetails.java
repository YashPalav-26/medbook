package com.medbook.security;

import com.medbook.entity.Role;
import com.medbook.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Spring Security {@link UserDetails} adapter over the existing {@link User} entity.
 *
 * <p>Field mapping (schema columns → Spring Security concepts):</p>
 * <ul>
 * <li>{@code email} → {@link #getUsername()},</li>
 * <li>{@code password_hash} → {@link #getPassword()} (already a BCrypt hash — the plaintext
 *     password is never stored or exposed),</li>
 * <li>{@code is_active} → {@link #isEnabled()},</li>
 * <li>{@code is_locked} → inverted into {@link #isAccountNonLocked()},</li>
 * <li>{@code roles} (ADMIN/DOCTOR/PATIENT) → authorities with the {@code ROLE_} prefix
 *     ({@code ROLE_ADMIN} etc.), the Spring Security convention that {@code hasRole("ADMIN")}
 *     and {@code hasAnyRole(...)} understand.</li>
 * </ul>
 *
 * <p>The wrapped entity stays reachable via {@link #getUser()} so callers that already hold
 * a transaction can read additional fields without a second lookup.</p>
 */
public class MedbookUserDetails implements UserDetails {

    private final User user;

    public MedbookUserDetails(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(Role::getName)
                .map(name -> new SimpleGrantedAuthority("ROLE_" + name))
                .sorted(Comparator.comparing(GrantedAuthority::getAuthority))
                .toList();
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !user.isLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
