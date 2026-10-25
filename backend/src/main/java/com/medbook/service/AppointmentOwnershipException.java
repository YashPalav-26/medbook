package com.medbook.service;

import org.springframework.security.access.AccessDeniedException;













public class AppointmentOwnershipException extends AccessDeniedException {

    public AppointmentOwnershipException(String message) {
        super(message);
    }
}
