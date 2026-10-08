package com.parking.infrastructure.adapter.in.rest.dto;

/**
 * Payload of every real-time event.
 *
 * @param changedSpace space that changed, or null in the snapshot sent on connection.
 */
public record OccupancyEventResponse(
        SpaceResponse changedSpace,
        OccupancyResponse occupancy
) {
}
