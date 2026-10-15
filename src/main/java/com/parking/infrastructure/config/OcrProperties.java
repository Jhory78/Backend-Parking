package com.parking.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param confidenceThreshold readings below it are flagged as {@code lowConfidence} for the operator.
 * @param timeout             connection and read timeout of the OCR engine.
 */
@ConfigurationProperties(prefix = "app.ocr")
public record OcrProperties(
        @DefaultValue("0.80") double confidenceThreshold,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue PlateRecognizer plateRecognizer
) {

    /**
     * Plate Recognizer cloud API (https://platerecognizer.com).
     *
     * @param apiKey  secret, from the environment. Without it every recognition falls back to manual entry.
     * @param regions region hint for the engine, e.g. {@code co} for Colombia.
     */
    public record PlateRecognizer(
            @DefaultValue("https://api.platerecognizer.com/v1/plate-reader/") String url,
            @DefaultValue("") String apiKey,
            @DefaultValue("co") String regions
    ) {
    }
}
