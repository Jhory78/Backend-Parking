package com.parking.application.port.in;

import com.parking.domain.entity.User;

/**
 * RF-44: update the personal data or credentials of an account.
 */
public interface UpdateUserUseCase {

    User updateUser(Long userId, UpdateUserCommand command);
}
