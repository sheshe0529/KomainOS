package com.komainos.inventario.dto;

import com.komainos.inventario.model.TipoDato;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Sin código: lo usa el script de análisis para informar la medición y no se modifica */
public record ActualizarMetricaPeticion(
        @NotBlank(message = "El nombre de la métrica es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
        String nombre,

        @Size(max = 100, message = "La unidad no puede superar los 100 caracteres")
        String unidad,

        @NotNull(message = "Indique el tipo de dato de la métrica")
        TipoDato tipoDato,

        boolean esLista) {
}
