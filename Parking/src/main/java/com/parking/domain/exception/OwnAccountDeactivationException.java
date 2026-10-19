package com.parking.domain.exception;

import com.parking.domain.exception.BusinessRuleException;

public class OwnAccountDeactivationException extends BusinessRuleException {

    public OwnAccountDeactivationException() {
        super("No puede desactivar su propia cuenta");
    }
}
