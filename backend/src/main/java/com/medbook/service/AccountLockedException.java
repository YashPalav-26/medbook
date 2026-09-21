package com.medbook.service;

/**
 * Thrown when a correctly authenticated user signs in to a locked account
 * ({@code users.is_locked = TRUE}).
 *
 * <p>The lock status is only revealed <em>after</em> the password has been verified, so the
 * login endpoint still cannot be used to probe whether an email address has an account.</p>
 */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("This account is locked. Please contact the clinic to unlock it.");
    }
}
