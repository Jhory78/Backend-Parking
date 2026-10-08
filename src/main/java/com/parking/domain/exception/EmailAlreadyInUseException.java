package com.parking.domain.exception;

import com.parking.domain.exception.BusinessRuleException;

public class EmailAlreadyInUseException extends BusinessRuleException {

    public EmailAlreadyInUseException() {
        super("El correo ya está en uso");
    }
}
