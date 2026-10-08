package com.parking.infrastructure.adapter.out.persistence;

import com.parking.application.port.out.SpaceRepositoryPort;
import com.parking.domain.entity.ParkingSpace;
import com.parking.domain.entity.SpaceStatus;
import com.parking.domain.entity.VehicleType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SpacePersistenceAdapter implements SpaceRepositoryPort {

    private final SpringDataParkingSpaceRepository springDataRepository;

    @Override
    public List<ParkingSpace> findAll() {
        return springDataRepository.findAllByOrderByCodeAsc();
    }

    @Override
    public List<ParkingSpace> findAll(VehicleType vehicleType, SpaceStatus status) {
        return springDataRepository.findAllFiltered(vehicleType, status);
    }

    @Override
    public Optional<ParkingSpace> findById(Long spaceId) {
        return springDataRepository.findById(spaceId);
    }

    @Override
    public ParkingSpace save(ParkingSpace space) {
        return springDataRepository.save(space);
    }
}
