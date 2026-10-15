package com.parking.domain.exception;

public class ActiveEntryNotFoundException extends ResourceNotFoundException {

    public ActiveEntryNotFoundException(String plate) {
        super("No hay un ingreso activo para la placa %s".formatted(plate));
    }
}
