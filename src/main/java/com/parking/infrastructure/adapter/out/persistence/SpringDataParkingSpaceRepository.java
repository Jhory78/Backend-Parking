package com.parking.infrastructure.adapter.out.persistence;

import com.parking.domain.entity.ParkingSpace;
import com.parking.domain.entity.SpaceStatus;
import com.parking.domain.entity.VehicleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

interface SpringDataParkingSpaceRepository extends JpaRepository<ParkingSpace, Long> {

    List<ParkingSpace> findAllByOrderByCodeAsc();

    @Query("""
            SELECT space FROM ParkingSpace space
            WHERE (:vehicleType IS NULL OR space.vehicleType = :vehicleType)
              AND (:status IS NULL OR space.status = :status)
            ORDER BY space.code
            """)
    List<ParkingSpace> findAllFiltered(@Param("vehicleType") VehicleType vehicleType,
                                       @Param("status") SpaceStatus status);
}
