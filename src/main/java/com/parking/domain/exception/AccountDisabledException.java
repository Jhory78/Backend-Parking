package com.parking.domain.exception;

/**
 * Correct credentials for a deactivated account (RF-41, RF-45).
 */
public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("La cuenta está desactivada");
    }
}
