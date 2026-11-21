package com.komainos.inventario.dto;

import com.komainos.inventario.model.ConfiguracionSistema;

import java.time.Instant;

public record ParametrosSistemaRespuesta(Integer maxEjecucionesConcurrentes, Integer maxDuracionTareaMinutos,
                                         Integer maxDuracionMopMinutos, Integer minutosExpiracionToken,
                                         Instant fechaActualizacion) {

    public static ParametrosSistemaRespuesta de(ConfiguracionSistema p) {
        return new ParametrosSistemaRespuesta(p.getMaxEjecucionesConcurrentes(), p.getMaxDuracionTareaMinutos(),
                p.getMaxDuracionMopMinutos(), p.getMinutosExpiracionToken(), p.getFechaActualizacion());
    }
}
