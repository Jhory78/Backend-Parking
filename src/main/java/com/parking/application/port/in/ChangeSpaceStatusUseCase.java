package com.parking.application.port.in;

import com.parking.domain.entity.ParkingSpace;
import com.parking.domain.entity.SpaceStatus;

/**
 * RF-16: mark a space as occupied or available. The new occupancy is pushed
 * to every screen and the entry barrier reacts at full capacity (RF-20..22).
 */
public interface ChangeSpaceStatusUseCase {

    ParkingSpace changeStatus(Long spaceId, SpaceStatus newStatus);
}
