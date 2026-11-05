package com.parking.infrastructure.adapter.in.rest.dto;

import java.time.Instant;

public record BackupResponse(String fileName, long sizeBytes, Instant createdAt) {
}
