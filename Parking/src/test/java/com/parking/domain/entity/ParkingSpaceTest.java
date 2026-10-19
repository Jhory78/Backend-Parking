package com.parking.domain.entity;

import com.parking.domain.exception.InvalidSpaceStatusChangeException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParkingSpaceTest {

    private static final Instant ENTRY_TIME = Instant.parse("2026-10-14T15:30:00Z");

    @Test
    void newSpaceIsAvailable() {
        ParkingSpace space = ParkingSpace.available("C-01", VehicleType.CAR);

        assertThat(space.getStatus()).isEqualTo(SpaceStatus.AVAILABLE);
        assertThat(space.isOccupied()).isFalse();
    }

    @Test
    void occupyAndRelease() {
        ParkingSpace space = ParkingSpace.available("C-01", VehicleType.CAR);

        space.occupy();
        assertThat(space.getStatus()).isEqualTo(SpaceStatus.OCCUPIED);

        space.release();
        assertThat(space.getStatus()).isEqualTo(SpaceStatus.AVAILABLE);
    }

    @Test
    void cannotOccupyAnOccupiedSpace() {
        ParkingSpace space = ParkingSpace.available("C-01", VehicleType.CAR);
        space.occupy();

        assertThatThrownBy(space::occupy)
                .isInstanceOf(InvalidSpaceStatusChangeException.class)
                .hasMessage("El espacio C-01 ya está ocupado");
    }

    @Test
    void cannotReleaseAnAvailableSpace() {
        ParkingSpace space = ParkingSpace.available("M-03", VehicleType.MOTORCYCLE);

        assertThatThrownBy(space::release)
                .isInstanceOf(InvalidSpaceStatusChangeException.class)
                .hasMessage("El espacio M-03 ya está disponible");
    }

    @Test
    void manualOccupationHasNoPlate() {
        ParkingSpace space = ParkingSpace.available("C-01", VehicleType.CAR);

        space.occupy();

        assertThat(space.getCurrentPlate()).isNull();
        assertThat(space.getOccupiedSince()).isNull();
        assertThat(space.hasRegisteredVehicle()).isFalse();
    }

    @Test
    void assignToStoresThePlateAndEntryTime() {
        ParkingSpace space = ParkingSpace.available("C-01", VehicleType.CAR);

        space.assignTo(LicensePlate.of("ABC123"), ENTRY_TIME);

        assertThat(space.isOccupied()).isTrue();
        assertThat(space.getCurrentPlate()).isEqualTo("ABC123");
        assertThat(space.getOccupiedSince()).isEqualTo(ENTRY_TIME);
    }

    @Test
    void cannotAssignAnOccupiedSpace() {
        ParkingSpace space = ParkingSpace.available("C-01", VehicleType.CAR);
        space.occupy();

        assertThatThrownBy(() -> space.assignTo(LicensePlate.of("ABC123"), ENTRY_TIME))
                .isInstanceOf(InvalidSpaceStatusChangeException.class)
                .hasMessage("El espacio C-01 ya está ocupado");
    }

    @Test
    void spaceWithARegisteredVehicleCannotBeReleasedByHand() {
        ParkingSpace space = ParkingSpace.available("C-01", VehicleType.CAR);
        space.assignTo(LicensePlate.of("ABC123"), ENTRY_TIME);

        assertThatThrownBy(space::release)
                .isInstanceOf(InvalidSpaceStatusChangeException.class)
                .hasMessage("La plaza tiene un vehículo registrado; registra su salida");
        assertThat(space.isOccupied()).isTrue();
    }

    @Test
    void vacateFreesTheSpaceAndForgetsTheVehicle() {
        ParkingSpace space = ParkingSpace.available("C-01", VehicleType.CAR);
        space.assignTo(LicensePlate.of("ABC123"), ENTRY_TIME);

        space.vacate();

        assertThat(space.getStatus()).isEqualTo(SpaceStatus.AVAILABLE);
        assertThat(space.getCurrentPlate()).isNull();
        assertThat(space.getOccupiedSince()).isNull();
    }

    @Test
    void changeStatusDelegatesToOccupyOrRelease() {
        ParkingSpace space = ParkingSpace.available("C-01", VehicleType.CAR);

        space.changeStatus(SpaceStatus.OCCUPIED);
        assertThat(space.isOccupied()).isTrue();

        space.changeStatus(SpaceStatus.AVAILABLE);
        assertThat(space.isOccupied()).isFalse();
    }
}
