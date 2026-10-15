package com.parking.infrastructure.adapter.out.persistence;

import com.parking.domain.entity.ParkingSpace;
import com.parking.domain.entity.SpaceStatus;
import com.parking.domain.entity.VehicleType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * {@code SELECT ... FOR UPDATE} over every space of the type, free or not, in code order.
     * <p>
     * Locking only "the first available" ({@code LIMIT 1}) is not enough: a transaction waiting
     * for that row finds it taken once it gets the lock and returns no space at all, both in
     * PostgreSQL and in H2, even if other spaces are free. Locking the whole type makes concurrent
     * entries of the same type wait for each other and then read the committed state.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT space FROM ParkingSpace space WHERE space.vehicleType = :vehicleType ORDER BY space.code")
    List<ParkingSpace> lockAllByVehicleType(@Param("vehicleType") VehicleType vehicleType);
}
