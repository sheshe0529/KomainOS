package com.komainos.inventario.dto;

import com.komainos.inventario.model.ModalidadPlanificacion;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * RF17 / RF70 / HU13 para un servidor. Las frecuencias son opcionales: si no
 * se envian se copian del nivel de criticidad (HU11 CA2).
 */
public record ConfiguracionPeticion(
        @Min(value = 1, message = "La frecuencia de revisión debe ser de al menos 1 día")
        Integer frecuenciaRevisionDias,

        @Min(value = 1, message = "La frecuencia de mantenimiento debe ser de al menos 1 día")
        Integer frecuenciaMantenimientoDias,

        @NotNull(message = "La modalidad de planificación es obligatoria")
        ModalidadPlanificacion modalidadPlanificacion) {
}
