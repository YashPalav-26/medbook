package com.medbook.service;

/**
 * Thrown when registration is attempted with an email that already has an account.
 *
 * <p>Duplicate rejection is unavoidable at registration time (the email must be unique),
 * unlike at login time where the failure message stays generic.</p>
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("An account with this email address already exists");
    }
}
