package com.parking.application.port.out;

import com.parking.domain.entity.OccupancyUpdate;

/**
 * Pushes occupancy changes to the operators' screens in real time (RF-16, RF-20).
 * Implementations must only deliver changes that were actually committed.
 */
public interface OccupancyNotifierPort {

    void publish(OccupancyUpdate update);
}
