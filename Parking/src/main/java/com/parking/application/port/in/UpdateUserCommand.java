package com.parking.application.port.in;

/**
 * New personal data and, optionally, new credentials of an account (RF-44).
 *
 * @param newPassword blank or null keeps the current password.
 */
public record UpdateUserCommand(String name, String email, String newPassword) {

    public boolean hasNewPassword() {
        return newPassword != null && !newPassword.isBlank();
    }
}
