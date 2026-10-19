package com.parking.domain.entity;

/**
 * Raw result of an OCR engine.
 *
 * @param text       text as the engine read it, not normalized.
 * @param confidence from 0 to 1.
 */
public record PlateReading(String text, double confidence) {
}
