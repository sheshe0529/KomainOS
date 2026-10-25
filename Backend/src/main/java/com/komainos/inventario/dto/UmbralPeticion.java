package com.komainos.inventario.dto;

import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.OperadorComparacion;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Métrica numérica: operador y al menos un valor. Métrica de lista: lista y al menos un nivel (RF16, R2.4) */
public record UmbralPeticion(
        /** No cambia una vez creado el umbral: al actualizar debe ser la misma */
        @NotNull(message = "Indique la métrica del umbral")
        Integer idMetrica,

        @NotBlank(message = "El nombre del umbral es obligatorio")
        @Size(max = 255, message = "El nombre no puede superar los 255 caracteres")
        String nombre,

        OperadorComparacion operador,

        @Digits(integer = 14, fraction = 6, message = "La advertencia admite hasta 14 enteros y 6 decimales")
        BigDecimal valorAdvertencia,

        @Digits(integer = 14, fraction = 6, message = "El crítico admite hasta 14 enteros y 6 decimales")
        BigDecimal valorCritico,

        Integer idListaReferencia,

        /** Nivel si falta algún elemento de la lista; nulo, no se evalúa */
        NivelAlerta nivelFaltante,

        /** Nivel si aparece algún elemento que no está en la lista; nulo, no se evalúa */
        NivelAlerta nivelSobrante,

        /** Al actualizar, la fechaActualizacion que leyó el formulario: si otro la cambió después, responde 409 */
        Instant versionEsperada) {
}
