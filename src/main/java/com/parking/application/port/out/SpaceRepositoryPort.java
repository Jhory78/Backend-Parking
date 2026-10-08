package com.parking.application.port.out;

import com.parking.domain.entity.ParkingSpace;
import com.parking.domain.entity.SpaceStatus;
import com.parking.domain.entity.VehicleType;

import java.util.List;
import java.util.Optional;

public interface SpaceRepositoryPort {

    List<ParkingSpace> findAll();

    /** Spaces ordered by code. A {@code null} filter matches any value. */
    List<ParkingSpace> findAll(VehicleType vehicleType, SpaceStatus status);

    Optional<ParkingSpace> findById(Long spaceId);

    ParkingSpace save(ParkingSpace space);
}
