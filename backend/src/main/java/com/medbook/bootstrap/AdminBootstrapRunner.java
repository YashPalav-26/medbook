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

/**
 * Initial-administrator bootstrap.
 *
 * <p>On startup, when {@link BootstrapProperties} is configured and the configured
 * email is not yet registered, this runner creates the configured administrator with
 * the ADMIN role. It is the deliberate, controlled answer to the chicken-and-egg
 * problem documented in {@code docs/MEDBOOK_WORKFLOW_AND_GAP_ANALYSIS.md}: without an
 * admin there is no way to create doctors, so a fresh database had no route to the
 * admin or doctor experiences.</p>
 *
 * <h3>Why the check is scoped to the configured email</h3>
 *
 * <p>The gate is {@code "does THIS account exist?"}, not "does any ADMIN exist?".
 * The account is identified by its email, so idempotency is a property of the email:
 * an absent email is created exactly once; a present email is returned untouched
 * (name, phone, password hash and roles are never modified by a later startup).
 * This keeps the promise to operators that the configured initial administrator
 * always comes up with the application — even if other administrators were created
 * later or the database was seeded differently — while never minting duplicates and
 * never overwriting anyone's credentials.</p>
 *
 * <h3>Safety</h3>
 * <ul>
 * <li>does nothing when the credentials are not configured — safe to disable by simply
 *     leaving {@code medbook.bootstrap.admin-email} / {@code medbook.bootstrap.admin-password}
 *     (or their {@code MEDBOOK_BOOTSTRAP_ADMIN_*} environment variables) unset;</li>
 * <li>idempotent by email: an existing account is returned as-is — no second account,
 *     no password overwrite, no duplicate role grant;</li>
 * <li>assigns only the ADMIN role and hashes the password with BCrypt;</li>
 * <li>is not exposed through any HTTP endpoint;</li>
 * <li>never logs the plaintext password (only the account email);</li>
 * <li>a failure is logged and swallowed so a misconfigured bootstrap cannot stop the
 *     application from starting.</li>
 * </ul>
 */
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
            // Never leak the configured password here: only the exception type/message.
            log.error("Admin bootstrap failed; starting without creating an administrator: {}",
                    ex.getMessage());
        }
    }
}