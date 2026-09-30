package com.komainos.planificacion.service.algoritmo;

import com.komainos.shared.model.Intervalo;

import java.time.Instant;
import java.util.List;

/**
 * Salidas del algoritmo (especificacion, seccion 7): se vuelcan en
 * {@code programacion_orden} y en las fechas previstas de {@code orden_detalle}.
 */
public record ResultadoPlanificacion(
        Instant fechaObjetivo,
        Instant inicio,
        Instant fin,
        Instant fechaEvaluacion,
        Intervalo ventanaAplicada,
        List<Tramo> tramos) {

    /** Reserva de un servidor dentro de la orden. */
    public record Tramo(Integer idServidor, int posicion, Instant inicio, Instant fin) {
    }
}
