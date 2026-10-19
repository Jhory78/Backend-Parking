package com.parking.application.port.out;

import com.parking.domain.entity.PlateReading;

import java.util.Optional;

/**
 * OCR engine that reads plates from images (RF-02). Swapping the engine (cloud API,
 * local Python service...) only means writing another adapter.
 */
public interface PlateRecognitionPort {

    /**
     * @return the most confident reading, or empty when the image has no plate or the
     *         engine is unavailable: callers fall back to manual entry (RF-03).
     */
    Optional<PlateReading> read(byte[] image);
}
