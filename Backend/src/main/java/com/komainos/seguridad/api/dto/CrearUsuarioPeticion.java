package com.komainos.seguridad.api.dto;

import com.komainos.seguridad.dominio.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearUsuarioPeticion(
        @NotBlank(message = "El código de usuario es obligatorio")
        @Size(max = 100, message = "El código no puede superar los 100 caracteres")
        String codigo,

        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 255, message = "El nombre completo no puede superar los 255 caracteres")
        String nombreCompleto,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
        String contrasena,

        @NotNull(message = "El rol es obligatorio")
        Rol rol) {
}
