package com.parking.infrastructure.adapter.out.persistence;

import com.parking.domain.entity.EntryStatus;
import com.parking.domain.entity.VehicleEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

interface SpringDataVehicleEntryRepository extends JpaRepository<VehicleEntry, Long>,
        JpaSpecificationExecutor<VehicleEntry> {

    Optional<VehicleEntry> findByPlateAndStatus(String plate, EntryStatus status);

    boolean existsByPlateAndStatus(String plate, EntryStatus status);

    @EntityGraph(attributePaths = {"space", "registeredBy"})
    List<VehicleEntry> findAllByStatusOrderByEntryTimeDescIdDesc(EntryStatus status);

    @Override
    @EntityGraph(attributePaths = {"space", "registeredBy"})
    Page<VehicleEntry> findAll(Specification<VehicleEntry> specification, Pageable pageable);
}
