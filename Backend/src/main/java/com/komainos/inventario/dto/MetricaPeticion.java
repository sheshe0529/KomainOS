package com.komainos.inventario.dto;

import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.TipoDato;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MetricaPeticion(
        @NotBlank(message = "El código de la métrica es obligatorio")
        @Size(max = 100, message = "El código no puede superar los 100 caracteres")
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$",
                message = "El código empieza con una letra y solo lleva letras, números y guiones bajos")
        String codigo,

        @NotBlank(message = "El nombre de la métrica es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
        String nombre,

        @Size(max = 500, message = "La descripción no puede superar los 500 caracteres")
        String descripcion,

        @Size(max = 100, message = "La unidad no puede superar los 100 caracteres")
        String unidad,

        @NotNull(message = "Indique el tipo de dato de la métrica")
        TipoDato tipoDato,

        /** Si informa un conjunto de elementos en lugar de un valor único */
        boolean esLista,

        /** Familia en la que la informa el análisis; sin valor, Linux y Windows (R3.1) */
        FamiliaSistemaOperativo soAplica) {
}
