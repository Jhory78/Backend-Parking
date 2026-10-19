package com.parking.domain.exception;

import com.parking.domain.exception.ResourceNotFoundException;

public class SpaceNotFoundException extends ResourceNotFoundException {

    public SpaceNotFoundException() {
        super("Espacio no encontrado");
    }
}
