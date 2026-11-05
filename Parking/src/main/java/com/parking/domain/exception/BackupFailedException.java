package com.parking.domain.exception;

/**
 * The database copy could not be generated or restored. Translated to HTTP 500; the detail is only logged.
 */
public class BackupFailedException extends RuntimeException {

    public BackupFailedException(String detail) {
        super(detail);
    }

    public BackupFailedException(String detail, Throwable cause) {
        super(detail, cause);
    }
}
