package com.parking.infrastructure.adapter.in.rest.dto;

import com.parking.domain.entity.VehicleType;
import jakarta.validation.constraints.NotBlank;

/**
 * @param plate       any format: {@code "abc-123"} is stored as {@code ABC123}.
 * @param vehicleType optional when the plate format implies it (RF-04).
 */
public record RegisterEntryRequest(

        @NotBlank(message = "La placa es obligatoria")
        String plate,

        VehicleType vehicleType
) {
}
