package com.parking.domain.entity;

import java.time.Duration;

/**
 * Credential handed to the client after a successful login (RF-40).
 */
public record AccessToken(String value, Duration expiresIn) {
}
