package com.medbook.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration for the controlled, server-side first-administrator bootstrap.
 *
 * <p>Following the same convention as {@link com.medbook.security.JwtProperties}, the
 * values resolve from a Spring property first and an environment variable second:
 * {@code medbook.bootstrap.admin-email} / {@code MEDBOOK_BOOTSTRAP_ADMIN_EMAIL} and
 * {@code medbook.bootstrap.admin-password} / {@code MEDBOOK_BOOTSTRAP_ADMIN_PASSWORD}.
 * No credential is ever committed: {@code application.properties} deliberately carries
 * none of these keys.</p>
 *
 * <p>When {@link #isConfigured()} is {@code false} the bootstrap is disabled and the
 * application starts normally — production deployments that provision administrators
 * some other way simply leave the variables unset.</p>
 */
@Component
public class BootstrapProperties {

    private final String adminEmail;
    private final String adminPassword;
    private final String adminFirstName;
    private final String adminLastName;
    private final String adminPhoneNumber;

    public BootstrapProperties(
            @Value("${medbook.bootstrap.admin-email:${MEDBOOK_BOOTSTRAP_ADMIN_EMAIL:}}") String adminEmail,
            @Value("${medbook.bootstrap.admin-password:${MEDBOOK_BOOTSTRAP_ADMIN_PASSWORD:}}") String adminPassword,
            @Value("${medbook.bootstrap.admin-first-name:${MEDBOOK_BOOTSTRAP_ADMIN_FIRST_NAME:Clinic}}") String adminFirstName,
            @Value("${medbook.bootstrap.admin-last-name:${MEDBOOK_BOOTSTRAP_ADMIN_LAST_NAME:Administrator}}") String adminLastName,
            @Value("${medbook.bootstrap.admin-phone-number:${MEDBOOK_BOOTSTRAP_ADMIN_PHONE_NUMBER:+10000000000}}") String adminPhoneNumber
    ) {
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
        this.adminFirstName = adminFirstName;
        this.adminLastName = adminLastName;
        this.adminPhoneNumber = adminPhoneNumber;
    }

    /**
     * The bootstrap runs only when both an email and a password are supplied. A blank
     * email or password means "disabled", never "create an admin with an empty secret".
     */
    public boolean isConfigured() {
        return adminEmail != null && !adminEmail.isBlank()
                && adminPassword != null && !adminPassword.isBlank();
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public String getAdminFirstName() {
        return adminFirstName;
    }

    public String getAdminLastName() {
        return adminLastName;
    }

    public String getAdminPhoneNumber() {
        return adminPhoneNumber;
    }
}
