package com.parking.infrastructure.adapter.out.barrier;

import com.parking.application.port.out.EntryBarrierPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Software barrier used while there is no physical hardware: it keeps the
 * state in memory and logs every change. A hardware adapter would implement
 * {@link EntryBarrierPort} and replace this one with a Spring profile.
 */
@Slf4j
@Component
public class SimulatedEntryBarrierAdapter implements EntryBarrierPort {

    private final AtomicBoolean blocked = new AtomicBoolean(false);

    @Override
    public void block() {
        if (blocked.compareAndSet(false, true)) {
            log.warn("Entry barrier BLOCKED: the parking lot is full");
        }
    }

    @Override
    public void unblock() {
        if (blocked.compareAndSet(true, false)) {
            log.info("Entry barrier unblocked: spaces available again");
        }
    }

    @Override
    public boolean isBlocked() {
        return blocked.get();
    }
}
