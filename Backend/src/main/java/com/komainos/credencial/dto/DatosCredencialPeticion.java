package com.komainos.credencial.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Datos identificativos: el secreto se cambia solo creando una versión nueva */
public record DatosCredencialPeticion(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 255, message = "El nombre admite hasta 255 caracteres")
        String nombre,

        @NotBlank(message = "El usuario de acceso es obligatorio")
        @Size(max = 255, message = "El usuario de acceso admite hasta 255 caracteres")
        String usuarioAcceso,

        @Size(max = 500, message = "La descripción admite hasta 500 caracteres")
        String descripcion) {
}
