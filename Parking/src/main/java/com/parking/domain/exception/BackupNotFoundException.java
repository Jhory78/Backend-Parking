package com.parking.domain.exception;

public class BackupNotFoundException extends ResourceNotFoundException {

    public BackupNotFoundException() {
        super("Copia de seguridad no encontrada");
    }
}
