package com.komainos.catalogo.dto;

import com.komainos.catalogo.model.TipoScript;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Datos del script sin código: el código se publica como versión de una implementación (RF25, RF26) */
public record ScriptPeticion(
        @NotBlank(message = "El nombre del script es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
        String nombre,

        @NotNull(message = "Indique si el script es de análisis o de mantenimiento")
        TipoScript tipo,

        @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
        String descripcion,

        boolean activo) {
}
