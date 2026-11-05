package com.parking.domain.entity;

import java.util.List;

/**
 * @param entries most recent entry first.
 * @param page    zero-based.
 */
public record EntryHistoryPage(List<VehicleEntry> entries, int page, int size, long totalElements, int totalPages) {
}
