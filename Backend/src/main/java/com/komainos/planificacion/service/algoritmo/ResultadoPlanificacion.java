package com.komainos.planificacion.service.algoritmo;

import com.komainos.shared.model.Intervalo;

import java.time.Instant;
import java.util.List;

public record ResultadoPlanificacion(
        Instant fechaObjetivo,
        Instant inicio,
        Instant fin,
        Instant fechaEvaluacion,
        Intervalo ventanaAplicada,
        List<Tramo> tramos) {

    public record Tramo(Integer idServidor, int posicion, Instant inicio, Instant fin) {
    }
}
