package com.komainos.planificacion.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fecha objetivo del siguiente mantenimiento (RF64)")
class FechaObjetivoTest {

    private static final Instant CIERRE = Instant.parse("2026-10-01T15:00:00Z");

    @ParameterizedTest(name = "30 días × {0} = {1} horas")
    @CsvSource({"0.50, 360", "0.75, 540", "1.00, 720", "1.25, 900"})
    @DisplayName("aplica los factores de incidencia, salud regular, estado normal y racha estable")
    void aplicaFactores(String factor, long horas) {
        assertThat(FechaObjetivo.calcular(CIERRE, 30, new BigDecimal(factor)))
                .isEqualTo(CIERRE.plus(Duration.ofHours(horas)));
    }

    @Test
    @DisplayName("conserva las fracciones de día en vez de redondear el factor")
    void conservaFracciones() {
        // 45 días × 0,75 = 33,75 días = 33 días y 18 horas.
        assertThat(FechaObjetivo.calcular(CIERRE, 45, new BigDecimal("0.75")))
                .isEqualTo(CIERRE.plus(Duration.ofDays(33)).plus(Duration.ofHours(18)));
    }
}
