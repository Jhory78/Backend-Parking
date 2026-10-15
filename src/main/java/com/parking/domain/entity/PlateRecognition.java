package com.parking.domain.entity;

/**
 * Plate proposed to the operator before registering an entry (RF-02). Nothing is stored.
 *
 * @param plate         normalized and corrected plate.
 * @param confidence    OCR confidence, from 0 to 1.
 * @param vehicleType   implied by the format, or {@code null} when the format is not recognized (RF-04).
 * @param alreadyInside the plate already has an active entry (RF-11).
 */
public record PlateRecognition(String plate, double confidence, VehicleType vehicleType, boolean alreadyInside) {

    public boolean validFormat() {
        return vehicleType != null;
    }
}
