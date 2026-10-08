package com.parking.domain.exception;

/**
 * Unknown email or wrong password. Both cases share the same exception so the
 * API never reveals which accounts exist (RF-41).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Credenciales inválidas");
    }
}
