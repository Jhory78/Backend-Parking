package com.parking.application.port.in;

import com.parking.domain.entity.PlateRecognition;

/**
 * RF-01, RF-02: reads the plate from a photo taken by the operator. Nothing is
 * registered: the operator confirms or corrects the plate and then calls
 * {@link RegisterEntryUseCase}.
 */
public interface RecognizePlateUseCase {

    /**
     * @param image JPEG or PNG, at most 5 MB.
     * @throws com.parking.domain.exception.PlateNotRecognizedException when no plate can be read (RF-03).
     */
    PlateRecognition recognize(byte[] image);
}
