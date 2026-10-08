package com.parking.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * @param timeout how long a real-time connection stays open; the client reconnects afterwards.
 */
@ConfigurationProperties(prefix = "app.spaces.stream")
public record SpacesStreamProperties(
        @DefaultValue("30m") Duration timeout
) {
}
