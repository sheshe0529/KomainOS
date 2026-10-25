package com.komainos.inventario.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** Sin alcance: para otro servidor, grupo, entorno o criticidad se registra otra excepción */
public record ActualizarExcepcionUmbralPeticion(
        @Digits(integer = 14, fraction = 6, message = "La advertencia admite hasta 14 enteros y 6 decimales")
        BigDecimal valorAdvertencia,

        @Digits(integer = 14, fraction = 6, message = "El crítico admite hasta 14 enteros y 6 decimales")
        BigDecimal valorCritico,

        Integer idListaReferencia,

        @Size(max = 500, message = "El motivo no puede superar los 500 caracteres")
        String motivo,

        /** Al actualizar, la fechaActualizacion que leyó el formulario: si otro la cambió después, responde 409 */
        Instant versionEsperada) {
}
