package com.komainos.inventario.api.dto;

import com.komainos.inventario.dominio.ParametrosSistema;

import java.time.Instant;

public record ParametrosSistemaRespuesta(Integer maxEjecucionesConcurrentes, Integer maxDuracionTareaMinutos,
                                         Integer maxDuracionMopMinutos, Integer minCiclosRachaEstable,
                                         Integer minutosExpiracionToken, Instant fechaActualizacion) {

    public static ParametrosSistemaRespuesta de(ParametrosSistema p) {
        return new ParametrosSistemaRespuesta(p.getMaxEjecucionesConcurrentes(), p.getMaxDuracionTareaMinutos(),
                p.getMaxDuracionMopMinutos(), p.getMinCiclosRachaEstable(), p.getMinutosExpiracionToken(),
                p.getFechaActualizacion());
    }
}
