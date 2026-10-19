package com.parking.domain.entity;

public enum OccupancyLevel {
    /** Below 80 % of the total capacity. */
    NORMAL,
    /** 80 % or more: early warning to the operator (RF-19, RF-20). */
    HIGH,
    /** 100 %: no space of any type is left, entry must be blocked (RF-21, RF-22). */
    FULL
}
