package com.komainos.planificacion.dto;

import com.komainos.planificacion.service.algoritmo.ResultadoPlanificacion;

import java.time.Instant;
import java.util.List;

/** Primer intervalo disponible propuesto por el algoritmo, sin crear la orden. */
public record PropuestaRespuesta(Instant fechaObjetivo, Instant inicio, Instant fin, Instant fechaEvaluacion,
                                 Instant inicioVentana, Instant finVentana, List<Tramo> tramos) {

    public record Tramo(Integer idServidor, int posicion, Instant inicio, Instant fin) {
    }

    public static PropuestaRespuesta de(ResultadoPlanificacion r) {
        return new PropuestaRespuesta(r.fechaObjetivo(), r.inicio(), r.fin(), r.fechaEvaluacion(),
                r.ventanaAplicada().inicio(), r.ventanaAplicada().fin(),
                r.tramos().stream().map(t -> new Tramo(t.idServidor(), t.posicion(), t.inicio(), t.fin())).toList());
    }
}
