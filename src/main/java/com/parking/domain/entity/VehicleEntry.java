package com.parking.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * Stay of a vehicle in the parking lot, from its entry to its exit (RF-05, RF-10).
 * Records are never deleted: they are the entry history.
 */
@Entity
@Table(name = "vehicle_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VehicleEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String plate;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 20)
    private VehicleType vehicleType;

    @ManyToOne(optional = false)
    @JoinColumn(name = "space_id", nullable = false)
    private ParkingSpace space;

    @Column(name = "entry_time", nullable = false)
    private Instant entryTime;

    @Column(name = "exit_time")
    private Instant exitTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntryStatus status;

    /**
     * Same as {@link #plate} while the entry is active and null once closed. Its UNIQUE
     * constraint stops a plate from having two active entries (RF-11), even under concurrency.
     */
    @Getter(AccessLevel.NONE)
    @Column(name = "active_plate", unique = true, length = 10)
    private String activePlate;

    /** RF-05, RF-10: the vehicle enters and parks in {@code space}, already assigned to it. */
    public static VehicleEntry register(LicensePlate plate, VehicleType vehicleType, ParkingSpace space,
                                        Instant entryTime) {
        if (space.getVehicleType() != vehicleType) {
            throw new IllegalArgumentException(
                    "Space %s is not for %s".formatted(space.getCode(), vehicleType));
        }
        VehicleEntry entry = new VehicleEntry();
        entry.plate = plate.value();
        entry.vehicleType = vehicleType;
        entry.space = space;
        entry.entryTime = entryTime;
        entry.status = EntryStatus.ACTIVE;
        entry.activePlate = plate.value();
        return entry;
    }

    /** The vehicle leaves. */
    public void close(Instant exitTime) {
        if (!isActive()) {
            throw new IllegalStateException("Entry %d is already closed".formatted(id));
        }
        if (exitTime.isBefore(entryTime)) {
            throw new IllegalArgumentException("Exit time is before the entry time");
        }
        this.exitTime = exitTime;
        this.status = EntryStatus.CLOSED;
        this.activePlate = null;
    }

    /** Whole minutes between entry and exit; null while the vehicle is inside. */
    public Long durationMinutes() {
        return exitTime == null ? null : Duration.between(entryTime, exitTime).toMinutes();
    }

    public boolean isActive() {
        return status == EntryStatus.ACTIVE;
    }
}
