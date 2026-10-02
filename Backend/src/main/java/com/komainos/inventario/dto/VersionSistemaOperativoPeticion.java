package com.komainos.inventario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** activo solo se aplica al actualizar: al crear siempre nace activa */
public record VersionSistemaOperativoPeticion(
        @NotBlank(message = "La versión es obligatoria")
        @Size(max = 150, message = "La versión no puede superar los 150 caracteres")
        String version,

        Boolean activo) {
}
