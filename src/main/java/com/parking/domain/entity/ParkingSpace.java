package com.parking.domain.entity;

import com.parking.domain.exception.InvalidSpaceStatusChangeException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A parking space for one vehicle type.
 * <p>
 * JPA annotations are allowed in the domain (see docs/ARCHITECTURE.md) to avoid a
 * duplicated persistence model.
 */
@Entity
@Table(name = "parking_spaces")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingSpace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpaceStatus status;

    /** Optimistic locking: two operators cannot take the same space at once. */
    @Version
    private Long version;

    public static ParkingSpace available(String code, VehicleType vehicleType) {
        ParkingSpace space = new ParkingSpace();
        space.code = code;
        space.vehicleType = vehicleType;
        space.status = SpaceStatus.AVAILABLE;
        return space;
    }

    public void occupy() {
        if (isOccupied()) {
            throw new InvalidSpaceStatusChangeException("El espacio %s ya está ocupado".formatted(code));
        }
        status = SpaceStatus.OCCUPIED;
    }

    public void release() {
        if (!isOccupied()) {
            throw new InvalidSpaceStatusChangeException("El espacio %s ya está disponible".formatted(code));
        }
        status = SpaceStatus.AVAILABLE;
    }

    public void changeStatus(SpaceStatus newStatus) {
        if (newStatus == SpaceStatus.OCCUPIED) {
            occupy();
        } else {
            release();
        }
    }

    public boolean isOccupied() {
        return status == SpaceStatus.OCCUPIED;
    }
}
