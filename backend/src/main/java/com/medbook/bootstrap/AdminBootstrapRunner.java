package com.medbook.bootstrap;

import com.medbook.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final BootstrapProperties properties;
    private final BootstrapAccountService accountService;

    public AdminBootstrapRunner(BootstrapProperties properties, BootstrapAccountService accountService) {
        this.properties = properties;
        this.accountService = accountService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isConfigured()) {
            log.info("Admin bootstrap is disabled (medbook.bootstrap.admin-email / "
                    + "medbook.bootstrap.admin-password not set).");
            return;
        }

        try {
            Optional<User> ensured = accountService.ensureUser(
                    properties.getAdminEmail(),
                    properties.getAdminPassword(),
                    properties.getAdminFirstName(),
                    properties.getAdminLastName(),
                    properties.getAdminPhoneNumber(),
                    BootstrapAccountService.ROLE_ADMIN);

            ensured.ifPresentOrElse(
                    user -> log.info("Bootstrap administrator account ready for {} (already existed: {})",
                            user.getEmail(), user.getId() != null),
                    () -> log.warn("Admin bootstrap returned no account; nothing was created."));
        } catch (RuntimeException ex) {

            log.error("Admin bootstrap failed; starting without creating an administrator: {}",
                    ex.getMessage());
        }
    }
}