package com.parking.domain.exception;

import lombok.Getter;

/**
 * A field of the request breaks a domain rule that bean validation cannot check
 * (e.g. a plate that becomes empty once normalized).
 * Shared kernel: plain Java. Translated to HTTP 400 with {@code fieldErrors.<field>}.
 */
@Getter
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }
}
