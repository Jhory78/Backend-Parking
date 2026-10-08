package com.parking.application.port.in;

import com.parking.domain.entity.Role;

import java.util.Objects;

/**
 * Data needed to create an account (RF-43).
 *
 * @param role optional: when null the account is created as {@link Role#OPERATOR}.
 */
public record CreateUserCommand(String name, String email, String password, Role role) {

    public Role roleOrDefault() {
        return Objects.requireNonNullElse(role, Role.OPERATOR);
    }
}
