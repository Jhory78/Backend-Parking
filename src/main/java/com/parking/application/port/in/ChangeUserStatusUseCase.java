package com.parking.application.port.in;

import com.parking.domain.entity.User;
import com.parking.domain.entity.UserStatus;

/**
 * RF-45: activate or deactivate an account without deleting its history.
 */
public interface ChangeUserStatusUseCase {

    /**
     * @param actorId account performing the change; it cannot deactivate itself.
     */
    User changeStatus(Long userId, UserStatus newStatus, Long actorId);
}
