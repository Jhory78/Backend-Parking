package com.parking.infrastructure.adapter.in.rest.dto;

import com.parking.domain.entity.OccupancyLevel;

import java.util.List;

/**
 * @param entryBlocked true while the entry barrier is blocked because the lot is full (RF-22).
 * @param alertMessage text to show the operator when {@code level} is HIGH or FULL; null otherwise.
 */
public record OccupancyResponse(
        int totalSpaces,
        int occupiedSpaces,
        int availableSpaces,
        double occupancyPercentage,
        OccupancyLevel level,
        boolean entryBlocked,
        String alertMessage,
        List<AvailabilityResponse> byVehicleType
) {
}
