package com.parking.application.port.in;

import com.parking.domain.entity.LicensePlate;
import com.parking.domain.entity.ParkingSpace;
import com.parking.domain.entity.VehicleType;

import java.time.Instant;

/**
 * Takes and frees spaces for registered vehicles. Like {@link ChangeSpaceStatusUseCase},
 * every change is pushed to the screens and the entry barrier reacts at full capacity.
 * Must run inside the caller's transaction.
 */
public interface SpaceAllocationUseCase {

    /**
     * RF-06..09: assigns the first available space of the type, in code order.
     * Two concurrent calls never get the same space.
     *
     * @throws com.parking.domain.exception.NoSpaceAvailableException when there is none
     *                                                                 or the barrier is blocked.
     */
    ParkingSpace assignFirstAvailable(VehicleType vehicleType, LicensePlate plate, Instant entryTime);

    /** Frees the space of a vehicle that leaves. */
    ParkingSpace vacate(Long spaceId);
}
