package com.komainos.catalogo;

import com.komainos.catalogo.service.CodigosMetrica;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pista para corregir el código de una métrica que el análisis no informó (DEC-59)")
class CodigosMetricaTest {

    @Test
    @DisplayName("«uptime» en lugar de «uptime_dias»: el mensaje dice qué código corregir y que la comparación es exacta")
    void codigoParecido() {
        assertThat(CodigosMetrica.pista(List.of("uptime_dias"), List.of("uptime", "hostname")))
                .isEqualTo(". El script informó «uptime», que no está en el catálogo: ¿debía ser «uptime_dias»? "
                        + "Los códigos se comparan de forma exacta");
        assertThat(CodigosMetrica.parecido("mem_uso", List.of("memoria", "mem_usage"))).contains("mem_usage");
        assertThat(CodigosMetrica.parecido("disco_uso", List.of("cpu", "carga"))).isEmpty();
    }

    @Test
    @DisplayName("Varias correcciones se listan juntas; sin parecido se listan los códigos desconocidos")
    void variosYSinParecido() {
        assertThat(CodigosMetrica.pista(List.of("uptime_dias", "cpu_uso"), List.of("uptime", "cpu_usage")))
                .isEqualTo(". Códigos informados que no están en el catálogo: «uptime» (¿«uptime_dias»?), "
                        + "«cpu_usage» (¿«cpu_uso»?). Los códigos se comparan de forma exacta");
        assertThat(CodigosMetrica.pista(List.of("disco_uso"), List.of("temperatura")))
                .isEqualTo(". Códigos informados que no están en el catálogo: «temperatura»");
        assertThat(CodigosMetrica.pista(List.of("disco_uso"), List.of())).isEmpty();
    }
}
