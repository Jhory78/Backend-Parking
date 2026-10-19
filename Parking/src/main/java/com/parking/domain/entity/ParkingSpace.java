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

import java.time.Instant;

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

    /** Plate of the registered vehicle parked here; null when free or occupied by hand. */
    @Column(name = "current_plate", length = 10)
    private String currentPlate;

    /** Entry time of the registered vehicle; null when free or occupied by hand. */
    @Column(name = "occupied_since")
    private Instant occupiedSince;

    public static ParkingSpace available(String code, VehicleType vehicleType) {
        ParkingSpace space = new ParkingSpace();
        space.code = code;
        space.vehicleType = vehicleType;
        space.status = SpaceStatus.AVAILABLE;
        return space;
    }

    /** Manual occupation (RF-16): no vehicle is registered, so there is no plate. */
    public void occupy() {
        if (isOccupied()) {
            throw new InvalidSpaceStatusChangeException("El espacio %s ya está ocupado".formatted(code));
        }
        status = SpaceStatus.OCCUPIED;
    }

    /** RF-09: the space is assigned to a registered vehicle. */
    public void assignTo(LicensePlate plate, Instant entryTime) {
        occupy();
        currentPlate = plate.value();
        occupiedSince = entryTime;
    }

    /** Manual release (RF-16). A registered vehicle frees its space only through its exit. */
    public void release() {
        if (!isOccupied()) {
            throw new InvalidSpaceStatusChangeException("El espacio %s ya está disponible".formatted(code));
        }
        if (hasRegisteredVehicle()) {
            throw new InvalidSpaceStatusChangeException("La plaza tiene un vehículo registrado; registra su salida");
        }
        status = SpaceStatus.AVAILABLE;
    }

    /** Exit of the registered vehicle. */
    public void vacate() {
        if (!isOccupied()) {
            throw new InvalidSpaceStatusChangeException("El espacio %s ya está disponible".formatted(code));
        }
        status = SpaceStatus.AVAILABLE;
        currentPlate = null;
        occupiedSince = null;
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

    public boolean hasRegisteredVehicle() {
        return currentPlate != null;
    }
}
