package com.parking.domain.exception;

/**
 * RF-11: the plate already has an active entry.
 */
public class VehicleAlreadyInsideException extends BusinessRuleException {

    public VehicleAlreadyInsideException(String plate) {
        super("El vehículo %s ya se encuentra dentro".formatted(plate));
    }
}
