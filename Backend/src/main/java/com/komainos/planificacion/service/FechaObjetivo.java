package com.komainos.planificacion.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Fecha objetivo del siguiente mantenimiento (RF64): fecha base mas la
 * periodicidad base multiplicada por el factor del resultado del ciclo.
 *
 * <p>El producto puede no ser un numero entero de dias (45 dias x 0,75 =
 * 33,75 dias); se conserva con precision de minutos en vez de redondear a
 * dias, para no alterar el factor que exige el requisito.
 */
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
