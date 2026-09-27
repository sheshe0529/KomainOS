package com.komainos.inventario.api.dto;

import com.komainos.inventario.dominio.ModalidadPlanificacion;
import com.komainos.inventario.dominio.ModoEjecucion;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * RF17 para grupos: agrega el modo de ejecucion de los integrantes. Las
 * frecuencias omitidas se toman de la criticidad efectiva del grupo.
 */
public record ConfiguracionGrupoPeticion(
        @Min(value = 1, message = "La frecuencia de revisión debe ser de al menos 1 día")
        Integer frecuenciaRevisionDias,

        @Min(value = 1, message = "La frecuencia de mantenimiento debe ser de al menos 1 día")
        Integer frecuenciaMantenimientoDias,

        @NotNull(message = "La modalidad de planificación es obligatoria")
        ModalidadPlanificacion modalidadPlanificacion,

        @NotNull(message = "El modo de ejecución es obligatorio para un grupo")
        ModoEjecucion modoEjecucion) {
}
