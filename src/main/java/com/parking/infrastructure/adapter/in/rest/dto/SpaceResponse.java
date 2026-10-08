package com.parking.infrastructure.adapter.in.rest.dto;

import com.parking.domain.entity.SpaceStatus;
import com.parking.domain.entity.VehicleType;

public record SpaceResponse(
        Long id,
        String code,
        VehicleType vehicleType,
        SpaceStatus status
) {
}
