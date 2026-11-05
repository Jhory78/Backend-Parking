package com.parking.domain.entity;

import java.time.Instant;

/**
 * Page of the entry history to read (RF-10).
 *
 * @param plate normalized part of a plate; null for any plate.
 * @param from  earliest entry time, inclusive; null for no limit.
 * @param to    latest entry time, exclusive; null for no limit.
 * @param page  zero-based.
 */
public record EntryHistoryQuery(String plate, Instant from, Instant to, int page, int size) {
}
