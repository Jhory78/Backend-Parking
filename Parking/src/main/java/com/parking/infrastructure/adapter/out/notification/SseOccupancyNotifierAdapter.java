package com.parking.infrastructure.adapter.out.notification;

import com.parking.application.port.out.OccupancyNotifierPort;
import com.parking.domain.entity.OccupancyUpdate;
import com.parking.infrastructure.adapter.in.rest.dto.OccupancyEventResponse;
import com.parking.infrastructure.adapter.in.rest.mapper.SpaceRestMapper;
import com.parking.infrastructure.config.SpacesStreamProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Real-time updates with Server-Sent Events: every connected screen receives an
 * {@value #EVENT_NAME} event with the changed space and the new occupancy.
 */
@Slf4j
@Component
public class SseOccupancyNotifierAdapter implements OccupancyNotifierPort {

    static final String EVENT_NAME = "occupancy";

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final SpaceRestMapper mapper;
    private final SpacesStreamProperties properties;

    public SseOccupancyNotifierAdapter(SpaceRestMapper mapper, SpacesStreamProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
    }

    /**
     * Opens a connection and immediately sends the current state, so the
     * client does not need a separate request to get in sync.
     */
    public SseEmitter subscribe(OccupancyUpdate snapshot) {
        SseEmitter emitter = new SseEmitter(properties.timeout().toMillis());
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError(error -> emitters.remove(emitter));

        emitters.add(emitter);
        send(emitter, mapper.toEvent(snapshot));
        return emitter;
    }

    /** Delivered after commit: a rolled-back change must never reach the screens. */
    @Override
    public void publish(OccupancyUpdate update) {
        OccupancyEventResponse event = mapper.toEvent(update);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    broadcast(event);
                }
            });
        } else {
            broadcast(event);
        }
    }

    private void broadcast(OccupancyEventResponse event) {
        emitters.forEach(emitter -> send(emitter, event));
    }

    private void send(SseEmitter emitter, OccupancyEventResponse event) {
        try {
            emitter.send(SseEmitter.event().name(EVENT_NAME).data(event, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            // Client disconnected: forget the connection
            log.debug("Dropping real-time connection: {}", ex.getMessage());
            emitters.remove(emitter);
        }
    }
}
