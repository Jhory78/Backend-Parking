package com.parking.domain.exception;

import com.parking.domain.exception.BusinessRuleException;

/**
 * Occupying an occupied space or releasing an available one.
 * Extends the shared-kernel exception so the API answers 409 Conflict.
 */
public class InvalidSpaceStatusChangeException extends BusinessRuleException {

    public InvalidSpaceStatusChangeException(String message) {
        super(message);
    }
}
