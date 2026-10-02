package com.komainos.seguridad.dto;

import jakarta.validation.constraints.NotBlank;

/** La contraseña de quien ya inició sesión, para ver o exportar secretos (RF08, RF13, HU05 CA1) */
public record ReautenticacionPeticion(
        @NotBlank(message = "Ingrese su contraseña para continuar")
        String contrasena) {
}
