package com.parking.application.port.in;

import com.parking.domain.entity.Occupancy;
import com.parking.domain.entity.OccupancyState;
import com.parking.domain.entity.ParkingSpace;
import com.parking.domain.entity.SpaceStatus;
import com.parking.domain.entity.VehicleType;

import java.util.List;
import java.util.Map;

/**
 * Parking space queries. {@code null} filters match any value.
 */
public interface FindSpacesUseCase {

    /** RF-18: status of every space, ordered by code. */
    List<ParkingSpace> findSpaces(VehicleType vehicleType, SpaceStatus status);

    /** RF-17: availability per vehicle type; only the requested type when given. */
    Map<VehicleType, Occupancy> findAvailability(VehicleType vehicleType);

    /** RF-19, RF-21, RF-22: occupancy level and entry barrier state. */
    OccupancyState getOccupancyState();
}
