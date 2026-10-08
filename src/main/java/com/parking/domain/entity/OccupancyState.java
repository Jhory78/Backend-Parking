package com.parking.domain.entity;

import com.parking.domain.entity.OccupancyReport;

/**
 * Current occupancy plus the state of the entry barrier.
 */
public record OccupancyState(OccupancyReport report, boolean entryBlocked) {
}
