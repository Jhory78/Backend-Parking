package com.parking.infrastructure.adapter.in.rest.dto;

import com.parking.domain.entity.EntryStatus;
import com.parking.domain.entity.VehicleType;

import java.time.Instant;

/**
 * @param exitTime        null while the vehicle is inside.
 * @param durationMinutes whole minutes of the stay; null while the vehicle is inside.
 */
public record EntryResponse(
        Long id,
        String plate,
        VehicleType vehicleType,
        AssignedSpace space,
        Instant entryTime,
        Instant exitTime,
        Long durationMinutes,
        EntryStatus status
) {

    public record AssignedSpace(Long id, String code) {
    }
}
