package com.parking.domain.entity;

import com.parking.domain.entity.ParkingSpace;

/**
 * Real-time message sent to the operators.
 *
 * @param changedSpace space whose status changed, or {@code null} for the
 *                     snapshot sent when a client connects.
 */
public record OccupancyUpdate(ParkingSpace changedSpace, OccupancyState state) {

    public static OccupancyUpdate snapshot(OccupancyState state) {
        return new OccupancyUpdate(null, state);
    }
}
