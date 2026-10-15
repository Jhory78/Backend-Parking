package com.parking.infrastructure.adapter.out.persistence;

import com.parking.application.port.out.VehicleEntryRepositoryPort;
import com.parking.domain.entity.EntryStatus;
import com.parking.domain.entity.VehicleEntry;
import com.parking.domain.exception.VehicleAlreadyInsideException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class VehicleEntryPersistenceAdapter implements VehicleEntryRepositoryPort {

    private final SpringDataVehicleEntryRepository springDataRepository;

    /**
     * The use case already checks for an active entry; the UNIQUE constraint on
     * {@code active_plate} catches two simultaneous entries of the same plate.
     */
    @Override
    public VehicleEntry save(VehicleEntry entry) {
        try {
            return springDataRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException ex) {
            throw new VehicleAlreadyInsideException(entry.getPlate());
        }
    }

    @Override
    public Optional<VehicleEntry> findActiveByPlate(String plate) {
        return springDataRepository.findByPlateAndStatus(plate, EntryStatus.ACTIVE);
    }

    @Override
    public boolean existsActiveByPlate(String plate) {
        return springDataRepository.existsByPlateAndStatus(plate, EntryStatus.ACTIVE);
    }

    @Override
    public List<VehicleEntry> findAllActive() {
        return springDataRepository.findAllByStatusOrderByEntryTimeDescIdDesc(EntryStatus.ACTIVE);
    }
}
