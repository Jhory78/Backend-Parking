package com.parking.infrastructure.adapter.out.persistence;

import com.parking.domain.entity.EntryStatus;
import com.parking.domain.entity.VehicleEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface SpringDataVehicleEntryRepository extends JpaRepository<VehicleEntry, Long> {

    Optional<VehicleEntry> findByPlateAndStatus(String plate, EntryStatus status);

    boolean existsByPlateAndStatus(String plate, EntryStatus status);

    @EntityGraph(attributePaths = "space")
    List<VehicleEntry> findAllByStatusOrderByEntryTimeDescIdDesc(EntryStatus status);
}
