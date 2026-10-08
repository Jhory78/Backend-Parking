package com.parking.application.port.in;

import com.parking.domain.entity.User;

/**
 * RF-43: create an operator (or administrator) account.
 */
public interface CreateUserUseCase {

    User createUser(CreateUserCommand command);
}
