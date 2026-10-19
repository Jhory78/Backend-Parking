package com.parking.infrastructure.adapter.in.rest.dto;

import com.parking.domain.entity.Role;
import com.parking.domain.entity.UserStatus;

public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        UserStatus status
) {
}
