package com.medbook.service;

import com.medbook.dto.AdminCreateRequest;
import com.medbook.entity.Role;
import com.medbook.entity.User;
import com.medbook.repository.RoleRepository;
import com.medbook.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;



























@Service
public class AdminAdminService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAdminService(UserRepository userRepository,
                             RoleRepository roleRepository,
                             PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }





    @Transactional(readOnly = true)
    public List<User> listAll() {
        return userRepository.findByRoles_NameOrderByIdAsc("ADMIN");
    }







    @Transactional
    public User create(AdminCreateRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not seeded"));


        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()),
                request.firstName().trim(), request.lastName().trim(), request.phoneNumber().trim());
        user.addRole(adminRole);
        return userRepository.save(user);
    }
}
