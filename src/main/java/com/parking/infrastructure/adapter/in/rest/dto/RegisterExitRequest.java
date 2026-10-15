package com.parking.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterExitRequest(

        @NotBlank(message = "La placa es obligatoria")
        String plate
) {
}
