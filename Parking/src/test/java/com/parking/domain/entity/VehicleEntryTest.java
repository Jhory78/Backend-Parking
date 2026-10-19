package com.parking.domain.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleEntryTest {

    private static final Instant ENTRY_TIME = Instant.parse("2026-10-14T15:30:00Z");
    private static final LicensePlate PLATE = LicensePlate.of("ABC123");

    private final ParkingSpace carSpace = ParkingSpace.available("C-01", VehicleType.CAR);

    @Test
    void newEntryIsActive() {
        VehicleEntry entry = VehicleEntry.register(PLATE, VehicleType.CAR, carSpace, ENTRY_TIME);

        assertThat(entry.isActive()).isTrue();
        assertThat(entry.getStatus()).isEqualTo(EntryStatus.ACTIVE);
        assertThat(entry.getPlate()).isEqualTo("ABC123");
        assertThat(entry.getEntryTime()).isEqualTo(ENTRY_TIME);
        assertThat(entry.getExitTime()).isNull();
        assertThat(entry.durationMinutes()).isNull();
    }

    @Test
    void closingSetsTheExitTimeAndDuration() {
        VehicleEntry entry = VehicleEntry.register(PLATE, VehicleType.CAR, carSpace, ENTRY_TIME);

        entry.close(ENTRY_TIME.plusSeconds(95 * 60 + 59));

        assertThat(entry.getStatus()).isEqualTo(EntryStatus.CLOSED);
        assertThat(entry.getExitTime()).isEqualTo(Instant.parse("2026-10-14T17:05:59Z"));
        assertThat(entry.durationMinutes()).isEqualTo(95L);
    }

    @Test
    void cannotBeClosedTwice() {
        VehicleEntry entry = VehicleEntry.register(PLATE, VehicleType.CAR, carSpace, ENTRY_TIME);
        entry.close(ENTRY_TIME.plusSeconds(60));

        assertThatThrownBy(() -> entry.close(ENTRY_TIME.plusSeconds(120)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void exitCannotBeBeforeTheEntry() {
        VehicleEntry entry = VehicleEntry.register(PLATE, VehicleType.CAR, carSpace, ENTRY_TIME);

        assertThatThrownBy(() -> entry.close(ENTRY_TIME.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void spaceMustMatchTheVehicleType() {
        assertThatThrownBy(() -> VehicleEntry.register(PLATE, VehicleType.MOTORCYCLE, carSpace, ENTRY_TIME))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
