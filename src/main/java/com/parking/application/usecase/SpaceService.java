package com.parking.application.usecase;

import com.parking.application.port.in.ChangeSpaceStatusUseCase;
import com.parking.application.port.in.FindSpacesUseCase;
import com.parking.application.port.out.EntryBarrierPort;
import com.parking.application.port.out.OccupancyNotifierPort;
import com.parking.application.port.out.SpaceRepositoryPort;
import com.parking.domain.entity.Occupancy;
import com.parking.domain.entity.OccupancyReport;
import com.parking.domain.entity.OccupancyState;
import com.parking.domain.entity.OccupancyUpdate;
import com.parking.domain.entity.ParkingSpace;
import com.parking.domain.entity.SpaceStatus;
import com.parking.domain.entity.VehicleType;
import com.parking.domain.exception.SpaceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Parking space use cases (RF-16..22).
 * <p>
 * Until entry/exit registration exists (Sprints 3-4) operators change the status
 * by hand; that use case will call {@link ChangeSpaceStatusUseCase} too.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpaceService implements FindSpacesUseCase, ChangeSpaceStatusUseCase {

    private final SpaceRepositoryPort spaceRepository;
    private final OccupancyNotifierPort occupancyNotifier;
    private final EntryBarrierPort entryBarrier;

    @Override
    public List<ParkingSpace> findSpaces(VehicleType vehicleType, SpaceStatus status) {
        return spaceRepository.findAll(vehicleType, status);
    }

    @Override
    public Map<VehicleType, Occupancy> findAvailability(VehicleType vehicleType) {
        OccupancyReport report = OccupancyReport.of(spaceRepository.findAll());
        if (vehicleType == null) {
            return new EnumMap<>(report.byVehicleType());
        }
        return Map.of(vehicleType, report.forVehicleType(vehicleType));
    }

    @Override
    public OccupancyState getOccupancyState() {
        return new OccupancyState(OccupancyReport.of(spaceRepository.findAll()), entryBarrier.isBlocked());
    }

    /** Also reacts to the new occupancy: real-time update and entry barrier at full capacity. */
    @Override
    @Transactional
    public ParkingSpace changeStatus(Long spaceId, SpaceStatus newStatus) {
        ParkingSpace space = spaceRepository.findById(spaceId).orElseThrow(SpaceNotFoundException::new);

        space.changeStatus(newStatus);
        ParkingSpace savedSpace = spaceRepository.save(space);

        OccupancyReport report = OccupancyReport.of(spaceRepository.findAll());
        updateEntryBarrier(report);
        occupancyNotifier.publish(new OccupancyUpdate(savedSpace, new OccupancyState(report, entryBarrier.isBlocked())));

        return savedSpace;
    }

    /** Leaves the barrier consistent with the stored occupancy when the application starts. */
    @EventListener(ApplicationReadyEvent.class)
    public void synchronizeEntryBarrier() {
        updateEntryBarrier(OccupancyReport.of(spaceRepository.findAll()));
    }

    private void updateEntryBarrier(OccupancyReport report) {
        if (report.overall().isFull()) {
            entryBarrier.block();
        } else {
            entryBarrier.unblock();
        }
    }
}
