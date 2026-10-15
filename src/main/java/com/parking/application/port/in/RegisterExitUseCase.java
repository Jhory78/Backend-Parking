package com.parking.application.port.in;

import com.parking.domain.entity.VehicleEntry;

/**
 * Exit of a vehicle: closes its active entry and frees its space.
 */
public interface RegisterExitUseCase {

    VehicleEntry registerExit(String plate);
}
