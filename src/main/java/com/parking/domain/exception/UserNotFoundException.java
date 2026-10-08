package com.parking.domain.exception;

import com.parking.domain.exception.ResourceNotFoundException;

public class UserNotFoundException extends ResourceNotFoundException {

    public UserNotFoundException() {
        super("Usuario no encontrado");
    }
}
