package com.parking.domain.exception;

import com.parking.domain.entity.VehicleType;

/**
 * RF-06..08: there is no free space for the vehicle type, or the entry barrier is blocked.
 */
public class NoSpaceAvailableException extends BusinessRuleException {

    public NoSpaceAvailableException(VehicleType vehicleType) {
        super("No hay espacios disponibles para " + pluralName(vehicleType));
    }

    private static String pluralName(VehicleType vehicleType) {
        return switch (vehicleType) {
            case CAR -> "carros";
            case MOTORCYCLE -> "motos";
        };
    }
}
