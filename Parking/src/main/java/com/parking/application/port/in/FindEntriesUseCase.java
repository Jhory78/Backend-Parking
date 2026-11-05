package com.parking.application.port.in;

import com.parking.domain.entity.EntryHistoryPage;
import com.parking.domain.entity.EntryHistoryQuery;
import com.parking.domain.entity.VehicleEntry;

import java.util.List;

public interface FindEntriesUseCase {

    /** RF-10: vehicles inside, most recent entry first. */
    List<VehicleEntry> findActiveEntries();

    /** RF-10: every stored entry, closed ones included, most recent first. */
    EntryHistoryPage findHistory(EntryHistoryQuery query);
}
