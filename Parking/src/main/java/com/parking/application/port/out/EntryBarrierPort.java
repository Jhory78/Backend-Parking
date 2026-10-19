package com.parking.application.port.out;

/**
 * Physical barrier at the parking entrance (RF-22).
 * Operations are idempotent: blocking an already blocked barrier does nothing.
 */
public interface EntryBarrierPort {

    void block();

    void unblock();

    boolean isBlocked();
}
