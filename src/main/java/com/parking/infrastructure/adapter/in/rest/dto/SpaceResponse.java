package com.parking.infrastructure.adapter.in.rest.dto;

import com.parking.domain.entity.SpaceStatus;
import com.parking.domain.entity.VehicleType;

import java.time.Instant;

/**
 * @param currentPlate  plate of the registered vehicle; null when free or occupied by hand.
 * @param occupiedSince entry time of that vehicle; null when free or occupied by hand.
 */
public record SpaceResponse(
        Long id,
        String code,
        VehicleType vehicleType,
        SpaceStatus status,
        String currentPlate,
        Instant occupiedSince
) {
}
