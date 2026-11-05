package com.parking.infrastructure.adapter.in.rest.dto;

import java.util.List;

/**
 * @param content most recent entry first.
 * @param page    zero-based.
 */
public record EntryHistoryResponse(
        List<EntryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
