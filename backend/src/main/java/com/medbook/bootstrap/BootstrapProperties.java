package com.medbook.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;















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
