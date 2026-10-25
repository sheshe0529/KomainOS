package com.komainos.seguridad.dto;

import com.komainos.seguridad.model.Rol;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ActualizarUsuarioPeticion(
        @NotBlank(message = "El nombre completo es obligatorio")
        @Size(max = 255, message = "El nombre completo no puede superar los 255 caracteres")
        String nombreCompleto,

        @NotNull(message = "El rol es obligatorio")
        Rol rol,

        /** fechaActualizacion que leyó el formulario: si otro la cambió después, responde 409 en vez de sobrescribir */
        Instant versionEsperada) {
}
