package com.parking.infrastructure.adapter.in.rest.dto;

import com.parking.domain.entity.VehicleType;

public record AvailabilityResponse(
        VehicleType vehicleType,
        int totalSpaces,
        int availableSpaces,
        int occupiedSpaces,
        double occupancyPercentage
) {
}
