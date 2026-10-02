package com.komainos.planificacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Se conserva con precisión de minutos en vez de redondear a días para no alterar el factor (RF64) */
public final class FechaObjetivo {

    private static final BigDecimal MINUTOS_POR_DIA = BigDecimal.valueOf(24 * 60);

    private FechaObjetivo() {
    }

    public static Instant calcular(Instant base, int periodicidadBaseDias, BigDecimal factor) {
        long minutos = BigDecimal.valueOf(periodicidadBaseDias)
                .multiply(factor)
                .multiply(MINUTOS_POR_DIA)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        return base.plusSeconds(minutos * 60);
    }
}
