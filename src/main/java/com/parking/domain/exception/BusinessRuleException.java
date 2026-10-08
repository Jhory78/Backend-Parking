package com.parking.domain.exception;

/**
 * A request is well formed but violates a business rule (e.g. duplicated email).
 * Shared kernel: plain Java, usable from any domain. Translated to HTTP 409 Conflict.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
