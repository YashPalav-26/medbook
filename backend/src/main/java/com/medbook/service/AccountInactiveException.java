package com.medbook.service;

/**
 * Thrown when a correctly authenticated user signs in to a deactivated account
 * ({@code users.is_active = FALSE}).
 *
 * <p>Like the lock status, the active status is only revealed after the password has been
 * verified.</p>
 */
public class AccountInactiveException extends RuntimeException {

    public AccountInactiveException() {
        super("This account is deactivated. Please contact the clinic.");
    }
}
