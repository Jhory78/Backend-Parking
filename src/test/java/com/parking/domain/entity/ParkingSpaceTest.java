package com.parking.domain.entity;

import com.parking.domain.exception.InvalidSpaceStatusChangeException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParkingSpaceTest {

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
    void changeStatusDelegatesToOccupyOrRelease() {
        ParkingSpace space = ParkingSpace.available("C-01", VehicleType.CAR);

        space.changeStatus(SpaceStatus.OCCUPIED);
        assertThat(space.isOccupied()).isTrue();

        space.changeStatus(SpaceStatus.AVAILABLE);
        assertThat(space.isOccupied()).isFalse();
    }
}
