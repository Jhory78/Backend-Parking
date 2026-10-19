package com.parking.domain.entity;

import com.parking.domain.exception.FieldValidationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LicensePlateTest {

    @Nested
    class Normalization {

        @ParameterizedTest
        @CsvSource({
                "abc-123, ABC123",
                "' ABC 123 ', ABC123",
                "abc12d, ABC12D",
                "Abc.12-D, ABC12D"
        })
        void upperCaseWithoutSpacesOrHyphens(String raw, String expected) {
            assertThat(LicensePlate.of(raw).value()).isEqualTo(expected);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "---"})
        void emptyPlateIsRejected(String raw) {
            assertThatThrownBy(() -> LicensePlate.of(raw))
                    .isInstanceOf(FieldValidationException.class)
                    .hasMessage("La placa es obligatoria")
                    .extracting("field").isEqualTo("plate");
        }

        @ParameterizedTest
        @ValueSource(strings = {"AB1", "ABCDE12345"})
        void tooShortOrTooLongIsRejected(String raw) {
            assertThatThrownBy(() -> LicensePlate.of(raw))
                    .isInstanceOf(FieldValidationException.class)
                    .hasMessage("La placa debe tener entre 4 y 8 letras o números");
        }
    }

    @Nested
    class Formats {

        @Test
        void carFormat() {
            LicensePlate plate = LicensePlate.of("ABC123");

            assertThat(plate.vehicleType()).contains(VehicleType.CAR);
            assertThat(plate.hasKnownFormat()).isTrue();
        }

        @Test
        void motorcycleFormat() {
            assertThat(LicensePlate.of("ABC12D").vehicleType()).contains(VehicleType.MOTORCYCLE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"CD1234", "R12345", "AB1234", "ABC1234", "ABCD12"})
        void otherFormatsAreValidButHaveNoVehicleType(String raw) {
            LicensePlate plate = LicensePlate.of(raw);

            assertThat(plate.vehicleType()).isEmpty();
            assertThat(plate.hasKnownFormat()).isFalse();
        }
    }

    @Nested
    class OcrCorrection {

        @ParameterizedTest
        @CsvSource({
                "0BC123, OBC123",   // 0 -> O in a letter position
                "A1C123, AIC123",   // 1 -> I
                "8BC123, BBC123",   // 8 -> B
                "5BC123, SBC123",   // 5 -> S
                "2BC123, ZBC123",   // 2 -> Z
                "ABCO23, ABC023",   // O -> 0 in a digit position
                "ABC1I3, ABC113",   // I -> 1
                "ABCB23, ABC823",   // B -> 8
                "ABCS23, ABC523",   // S -> 5
                "ABCZ23, ABC223"    // Z -> 2
        })
        void fixesTypicalMistakesByPosition(String ocrText, String expected) {
            assertThat(LicensePlate.fromOcr(ocrText)).map(LicensePlate::value).contains(expected);
        }

        @Test
        void lastCharacterIsLeftAloneBecauseItDecidesTheVehicleType() {
            assertThat(LicensePlate.fromOcr("ABC12S")).map(LicensePlate::value).contains("ABC12S");
            assertThat(LicensePlate.fromOcr("ABC125")).map(LicensePlate::value).contains("ABC125");
        }

        @Test
        void normalizesBeforeCorrecting() {
            LicensePlate plate = LicensePlate.fromOcr("abc-1o3").orElseThrow();

            assertThat(plate.value()).isEqualTo("ABC103");
            assertThat(plate.vehicleType()).contains(VehicleType.CAR);
        }

        @Test
        void otherLengthsAreNotCorrected() {
            assertThat(LicensePlate.fromOcr("R0234")).map(LicensePlate::value).contains("R0234");
            assertThat(LicensePlate.fromOcr("AB1O2345")).map(LicensePlate::value).contains("AB1O2345");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"--", "AB", "TOOLONGPLATE"})
        void textThatCannotBeAPlateIsEmpty(String ocrText) {
            assertThat(LicensePlate.fromOcr(ocrText)).isEmpty();
        }
    }
}
