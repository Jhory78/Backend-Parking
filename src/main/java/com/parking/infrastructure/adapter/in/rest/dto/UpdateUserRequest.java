package com.parking.infrastructure.adapter.in.rest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Updates the personal data and credentials of an account (RF-44).
 *
 * @param password optional: blank or missing keeps the current password.
 */
public record UpdateUserRequest(

        @NotBlank(message = "El nombre es obligatorio")
        String name,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String email,

        @Pattern(regexp = "^$|^.{6,}$", message = "La contraseña debe tener al menos 6 caracteres")
        String password
) {
}
