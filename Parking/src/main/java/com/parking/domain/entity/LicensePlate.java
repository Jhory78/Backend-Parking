package com.parking.domain.entity;

import com.parking.domain.exception.FieldValidationException;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Normalized vehicle plate: upper case, letters and digits only ({@code "abc-123"} → {@code ABC123}).
 * <p>
 * Colombian formats (RF-04): car {@code ABC123}, motorcycle {@code ABC12D}. Plates with another
 * format (foreign, diplomatic...) are valid too, but their vehicle type must be given explicitly.
 */
public record LicensePlate(String value) {

    public static final String FIELD = "plate";

    private static final Pattern VALID = Pattern.compile("^[A-Z0-9]{4,8}$");
    private static final Pattern CAR = Pattern.compile("^[A-Z]{3}[0-9]{3}$");
    private static final Pattern MOTORCYCLE = Pattern.compile("^[A-Z]{3}[0-9]{2}[A-Z]$");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]");

    /** Characters that OCR engines confuse, in the positions that must hold a letter... */
    private static final Map<Character, Character> DIGIT_TO_LETTER = Map.of(
            '0', 'O', '1', 'I', '8', 'B', '5', 'S', '2', 'Z');
    /** ...and in the positions that must hold a digit. */
    private static final Map<Character, Character> LETTER_TO_DIGIT = Map.of(
            'O', '0', 'I', '1', 'B', '8', 'S', '5', 'Z', '2');

    public LicensePlate {
        if (value == null || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Plate is not normalized: " + value);
        }
    }

    /** Plate typed or confirmed by the operator. */
    public static LicensePlate of(String rawPlate) {
        String normalized = normalize(rawPlate);
        if (normalized.isEmpty()) {
            throw new FieldValidationException(FIELD, "La placa es obligatoria");
        }
        if (!VALID.matcher(normalized).matches()) {
            throw new FieldValidationException(FIELD, "La placa debe tener entre 4 y 8 letras o números");
        }
        return new LicensePlate(normalized);
    }

    /**
     * Plate read by the OCR engine (RF-02), with its typical mistakes fixed.
     * Empty when the text cannot be a plate.
     */
    public static Optional<LicensePlate> fromOcr(String ocrText) {
        String corrected = correctOcrErrors(normalize(ocrText));
        return VALID.matcher(corrected).matches() ? Optional.of(new LicensePlate(corrected)) : Optional.empty();
    }

    public static String normalize(String rawPlate) {
        if (rawPlate == null) {
            return "";
        }
        return NOT_ALPHANUMERIC.matcher(rawPlate.toUpperCase(Locale.ROOT)).replaceAll("");
    }

    /**
     * Both Colombian formats have letters in positions 1-3 and digits in positions 4-5,
     * so those are fixed (O/0, I/1, B/8, S/5, Z/2). The last character is left alone:
     * a digit means car and a letter means motorcycle, and there is no way to tell which one was meant.
     */
    static String correctOcrErrors(String plate) {
        if (plate.length() != 6) {
            return plate;
        }
        char[] chars = plate.toCharArray();
        for (int i = 0; i < 3; i++) {
            chars[i] = DIGIT_TO_LETTER.getOrDefault(chars[i], chars[i]);
        }
        for (int i = 3; i < 5; i++) {
            chars[i] = LETTER_TO_DIGIT.getOrDefault(chars[i], chars[i]);
        }
        return new String(chars);
    }

    /** RF-04: vehicle type implied by the format; empty when the format is not a Colombian one. */
    public Optional<VehicleType> vehicleType() {
        if (CAR.matcher(value).matches()) {
            return Optional.of(VehicleType.CAR);
        }
        if (MOTORCYCLE.matcher(value).matches()) {
            return Optional.of(VehicleType.MOTORCYCLE);
        }
        return Optional.empty();
    }

    public boolean hasKnownFormat() {
        return vehicleType().isPresent();
    }

    @Override
    public String toString() {
        return value;
    }
}
