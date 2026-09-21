package com.medbook.service;

/**
 * Thrown when login credentials do not match any account.
 *
 * <p>The message is deliberately generic: it says neither "unknown email" nor "wrong
 * password", so the login endpoint cannot be used to discover which emails have accounts.
 * The account's failed-login counter is incremented before this exception is thrown.</p>
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
