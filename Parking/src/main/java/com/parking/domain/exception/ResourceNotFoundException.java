package com.parking.domain.exception;

/**
 * The requested resource does not exist.
 * Shared kernel: plain Java, usable from any domain. Translated to HTTP 404 Not Found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
