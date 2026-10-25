package com.medbook.service;








public class AccountInactiveException extends RuntimeException {

    public AccountInactiveException() {
        super("This account is deactivated. Please contact the clinic.");
    }
}
