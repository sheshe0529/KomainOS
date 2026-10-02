package com.komainos.planificacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "komainos.planificacion")
public record PropiedadesPlanificacion(String zonaHoraria, int horizonteDias, ProcesoAutomatico procesoAutomatico) {

    public ZoneId zona() {
        return ZoneId.of(zonaHoraria == null ? "America/Lima" : zonaHoraria);
    }

    public Duration horizonte() {
        return Duration.ofDays(horizonteDias <= 0 ? 365 : horizonteDias);
    }

    public record ProcesoAutomatico(boolean habilitado, Duration intervalo) {
    }
}
