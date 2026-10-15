package com.parking.application.port.in;

import com.parking.domain.entity.VehicleType;

/**
 * Entry confirmed by the operator.
 *
 * @param plate       as typed or recognized; it is normalized.
 * @param vehicleType optional when the plate format implies it (RF-04).
 */
public record RegisterEntryCommand(String plate, VehicleType vehicleType) {
}
