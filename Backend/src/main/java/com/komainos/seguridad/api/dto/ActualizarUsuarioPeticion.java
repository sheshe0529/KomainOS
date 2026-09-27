package com.komainos.seguridad.api.dto;

import com.komainos.seguridad.dominio.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ActualizarUsuarioPeticion(
        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 255, message = "El nombre completo no puede superar los 255 caracteres")
        String nombreCompleto,

        @NotNull(message = "El rol es obligatorio")
        Rol rol) {
}
