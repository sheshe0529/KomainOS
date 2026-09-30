package com.komainos.seguridad.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginPeticion(
        @NotBlank(message = "El código de usuario es obligatorio")
        String codigo,

        @NotBlank(message = "La contraseña es obligatoria")
        String contrasena) {
}
