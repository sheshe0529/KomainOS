package com.komainos.inventario.dto;

import com.komainos.inventario.model.AlcanceExcepcion;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Una sola dimensión por excepción; en un umbral numérico lleva valores y en uno de lista, la lista (R2.4) */
public record ExcepcionUmbralPeticion(
        @NotNull(message = "Indique si la excepción es para un servidor, un grupo, un entorno o una criticidad")
        AlcanceExcepcion alcance,

        @NotNull(message = "Indique a quién aplica la excepción")
        Integer idObjetivo,

        @Digits(integer = 14, fraction = 6, message = "La advertencia admite hasta 14 enteros y 6 decimales")
        BigDecimal valorAdvertencia,

        @Digits(integer = 14, fraction = 6, message = "El crítico admite hasta 14 enteros y 6 decimales")
        BigDecimal valorCritico,

        Integer idListaReferencia,

        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String motivo) {
}
