package com.parking.domain.exception;

/**
 * RF-03: the OCR engine failed or found no plate. Translated to HTTP 422; the
 * frontend falls back to manual entry.
 */
public class PlateNotRecognizedException extends RuntimeException {

    public PlateNotRecognizedException() {
        super("No se pudo leer la placa, ingrésala manualmente");
    }
}
