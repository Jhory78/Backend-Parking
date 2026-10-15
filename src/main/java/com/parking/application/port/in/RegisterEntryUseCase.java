package com.parking.application.port.in;

import com.parking.domain.entity.VehicleEntry;

/**
 * RF-04..11: registers the entry of a vehicle and assigns it the first free
 * space of its type. The new occupancy is pushed to every screen.
 */
public interface RegisterEntryUseCase {

    VehicleEntry registerEntry(RegisterEntryCommand command);
}
