package com.medbook.service;

public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("This account is locked. Please contact the clinic to unlock it.");
    }
}
