package com.parking.infrastructure.adapter.in.rest.dto;

import com.parking.domain.entity.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(

        @NotNull(message = "El estado es obligatorio")
        UserStatus status
) {
}
