package com.parking.application.port.out;

import com.parking.domain.entity.User;
import com.parking.domain.exception.AccountDisabledException;
import com.parking.domain.exception.InvalidCredentialsException;

/**
 * Checks an email and password against the stored accounts (RF-41).
 */
public interface CredentialsAuthenticatorPort {

    /**
     * @throws InvalidCredentialsException unknown email or wrong password.
     * @throws AccountDisabledException    correct credentials for a deactivated account.
     */
    User authenticate(String email, String password);
}
