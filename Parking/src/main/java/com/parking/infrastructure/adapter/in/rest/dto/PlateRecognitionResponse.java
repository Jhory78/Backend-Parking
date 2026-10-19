package com.parking.infrastructure.adapter.in.rest.dto;

import com.parking.domain.entity.VehicleType;

/**
 * Plate proposed by the OCR. Nothing is registered until the operator confirms it.
 *
 * @param lowConfidence {@code confidence} is below {@code app.ocr.confidence-threshold}: ask the operator to check it.
 * @param vehicleType   implied by the format; null when the format is not recognized.
 * @param validFormat   the plate has a Colombian car or motorcycle format.
 * @param alreadyInside the plate already has an active entry.
 */
public record PlateRecognitionResponse(
        String plate,
        double confidence,
        boolean lowConfidence,
        VehicleType vehicleType,
        boolean validFormat,
        boolean alreadyInside
) {
}
