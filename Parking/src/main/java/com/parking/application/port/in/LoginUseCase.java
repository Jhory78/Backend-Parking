package com.parking.application.port.in;

import com.parking.domain.entity.AccessToken;

/**
 * RF-40, RF-41: log in with email and password.
 */
public interface LoginUseCase {

    AccessToken login(String email, String password);
}
