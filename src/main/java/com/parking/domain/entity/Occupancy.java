package com.parking.domain.entity;

/**
 * Occupancy of a group of spaces (the whole parking lot or one vehicle type).
 */
public record Occupancy(int totalSpaces, int occupiedSpaces) {

    public static final int HIGH_OCCUPANCY_PERCENTAGE = 80;

    public Occupancy {
        if (totalSpaces < 0 || occupiedSpaces < 0 || occupiedSpaces > totalSpaces) {
            throw new IllegalArgumentException(
                    "Invalid occupancy: %d occupied of %d".formatted(occupiedSpaces, totalSpaces));
        }
    }

    public int availableSpaces() {
        return totalSpaces - occupiedSpaces;
    }

    /** Percentage with one decimal. A lot without spaces counts as full. */
    public double percentage() {
        if (totalSpaces == 0) {
            return 100.0;
        }
        return Math.round(occupiedSpaces * 1000.0 / totalSpaces) / 10.0;
    }

    public OccupancyLevel level() {
        if (isFull()) {
            return OccupancyLevel.FULL;
        }
        // Integer arithmetic: avoids rounding errors right at the threshold
        boolean high = occupiedSpaces * 100L >= (long) totalSpaces * HIGH_OCCUPANCY_PERCENTAGE;
        return high ? OccupancyLevel.HIGH : OccupancyLevel.NORMAL;
    }

    public boolean isFull() {
        return availableSpaces() == 0;
    }
}
