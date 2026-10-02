package com.komainos.inventario.dto;

import com.komainos.inventario.model.FamiliaSistemaOperativo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** activo solo se aplica al actualizar: al crear siempre nace activo */
public record SistemaOperativoPeticion(
        @NotBlank(message = "El nombre del sistema operativo es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String nombre,

        @NotNull(message = "La familia del sistema operativo es obligatoria")
        FamiliaSistemaOperativo familia,

        Boolean activo) {
}
