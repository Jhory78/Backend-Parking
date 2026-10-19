package com.parking.application.port.out;

/**
 * One-way password hashing. Passwords are never stored in plain text.
 */
public interface PasswordHasherPort {

    String hash(String rawPassword);
}
