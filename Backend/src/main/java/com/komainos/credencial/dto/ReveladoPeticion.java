package com.komainos.credencial.dto;

import jakarta.validation.constraints.NotBlank;

/** La contraseña de quien ya inició sesión, para reautenticarlo (RF08, HU05 CA1) */
public record ReveladoPeticion(
        @NotBlank(message = "Ingrese su contraseña para continuar")
        String contrasena) {
}
