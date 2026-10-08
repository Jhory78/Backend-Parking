package com.parking.infrastructure.adapter.in.rest.dto;

import com.parking.domain.entity.SpaceStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSpaceStatusRequest(

        @NotNull(message = "El estado es obligatorio")
        SpaceStatus status
) {
}
