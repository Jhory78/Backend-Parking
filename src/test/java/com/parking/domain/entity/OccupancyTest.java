package com.parking.domain.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OccupancyTest {

    @ParameterizedTest(name = "{1} of {0} occupied -> {2}")
    @CsvSource({
            "30, 0,  NORMAL",
            "30, 23, NORMAL",  // 76.7 %
            "30, 24, HIGH",    // exactly 80 %
            "30, 29, HIGH",
            "30, 30, FULL",
            "10, 8,  HIGH",
            "7,  5,  NORMAL",  // 71.4 %
            "7,  6,  HIGH",    // 85.7 %
            "0,  0,  FULL"     // a lot without spaces cannot accept vehicles
    })
    void levelFollowsThe80And100PercentThresholds(int total, int occupied, OccupancyLevel expected) {
        assertThat(new Occupancy(total, occupied).level()).isEqualTo(expected);
    }

    @Test
    void percentageIsRoundedToOneDecimal() {
        assertThat(new Occupancy(30, 23).percentage()).isEqualTo(76.7);
        assertThat(new Occupancy(30, 24).percentage()).isEqualTo(80.0);
        assertThat(new Occupancy(3, 1).percentage()).isEqualTo(33.3);
    }

    @Test
    void availableSpacesAreTheFreeOnes() {
        Occupancy occupancy = new Occupancy(30, 12);

        assertThat(occupancy.availableSpaces()).isEqualTo(18);
        assertThat(occupancy.isFull()).isFalse();
    }

    @Test
    void rejectsImpossibleValues() {
        assertThatThrownBy(() -> new Occupancy(10, 11)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Occupancy(-1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reportSplitsOccupancyByVehicleType() {
        ParkingSpace occupiedCar = ParkingSpace.available("C-01", VehicleType.CAR);
        occupiedCar.occupy();
        List<ParkingSpace> spaces = List.of(
                occupiedCar,
                ParkingSpace.available("C-02", VehicleType.CAR),
                ParkingSpace.available("M-01", VehicleType.MOTORCYCLE));

        OccupancyReport report = OccupancyReport.of(spaces);

        assertThat(report.overall()).isEqualTo(new Occupancy(3, 1));
        assertThat(report.forVehicleType(VehicleType.CAR)).isEqualTo(new Occupancy(2, 1));
        assertThat(report.forVehicleType(VehicleType.MOTORCYCLE)).isEqualTo(new Occupancy(1, 0));
    }
}
