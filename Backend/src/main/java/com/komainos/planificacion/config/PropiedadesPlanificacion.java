package com.komainos.planificacion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Parametros tecnicos de la planificacion.
 *
 * @param zonaHoraria     zona en la que se interpretan las ventanas (DEC-06)
 * @param horizonteDias   limite de busqueda del algoritmo (DEC-12)
 */
@ConfigurationProperties(prefix = "komainos.planificacion")
public record PropiedadesPlanificacion(String zonaHoraria, int horizonteDias, ProcesoAutomatico procesoAutomatico) {

    public ZoneId zona() {
        return ZoneId.of(zonaHoraria == null ? "America/Lima" : zonaHoraria);
    }

    public Duration horizonte() {
        return Duration.ofDays(horizonteDias <= 0 ? 365 : horizonteDias);
    }

    /** Proceso programado que genera las ordenes automaticas (RF27, RF29). */
    public record ProcesoAutomatico(boolean habilitado, Duration intervalo) {
    }
}
