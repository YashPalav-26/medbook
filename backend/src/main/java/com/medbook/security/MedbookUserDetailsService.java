package com.medbook.security;

import com.medbook.entity.User;
import com.medbook.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Spring Security {@link UserDetailsService} backed by the existing {@code users} table.
 *
 * <p>Spring Security asks for a user by "username"; in MedBook the sign-in identity is the
 * email address, so this adapter translates between the two conventions. Emails are stored
 * and looked up lower-cased (the {@code utf8mb4_unicode_ci} column collation already makes
 * the database comparison case-insensitive).</p>
 *
 * <p>Used today by the authentication service to load the account being signed in; it is
 * also the natural extension point for future features that need the full user record.</p>
 */
@Service
public class MedbookUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public MedbookUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Load a user by email address.
     *
     * @param email the email used at sign-in
     * @return the Spring Security view of the user
     * @throws UsernameNotFoundException when no user exists for that email
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByEmail(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email: " + normalized));
        return new MedbookUserDetails(user);
    }
}
