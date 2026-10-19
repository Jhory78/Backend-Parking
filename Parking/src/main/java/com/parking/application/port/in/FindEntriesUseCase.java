package com.parking.application.port.in;

import com.parking.domain.entity.VehicleEntry;

import java.util.List;

public interface FindEntriesUseCase {

    /** RF-10: vehicles inside, most recent entry first. */
    List<VehicleEntry> findActiveEntries();
}
