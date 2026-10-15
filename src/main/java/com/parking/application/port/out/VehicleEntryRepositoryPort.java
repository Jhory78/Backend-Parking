package com.parking.application.port.out;

import com.parking.domain.entity.VehicleEntry;

import java.util.List;
import java.util.Optional;

public interface VehicleEntryRepositoryPort {

    /**
     * @throws com.parking.domain.exception.VehicleAlreadyInsideException when another active
     *         entry with the same plate was stored at the same time.
     */
    VehicleEntry save(VehicleEntry entry);

    Optional<VehicleEntry> findActiveByPlate(String plate);

    boolean existsActiveByPlate(String plate);

    /** Most recent entry first. */
    List<VehicleEntry> findAllActive();
}
