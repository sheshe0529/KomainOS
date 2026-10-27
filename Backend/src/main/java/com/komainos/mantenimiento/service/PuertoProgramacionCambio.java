package com.komainos.mantenimiento.service;

import com.komainos.mantenimiento.model.Orden;
import com.komainos.shared.model.Actor;

import java.time.Instant;

/**
 * Lo que el ciclo necesita de la planificación sin depender de ella: la implementa planificación, que ya depende del
 * mantenimiento (DEC-27).
 */
public interface PuertoProgramacionCambio {

    /**
     * RF31, RF40: orden de prioridad alta que empieza ahora o, si es por condición crítica fuera de ventana, en la
     * siguiente ventana disponible.
     *
     * @param idEvaluacionOrigen la revisión que detectó la condición crítica; nula en un lanzamiento inmediato
     */
    Orden inmediata(Integer idServidor, Integer idGrupo, boolean confirmarFueraVentana, String motivo,
                    Integer idEvaluacionOrigen, Actor actor);

    /** RF48, CP122: comprueba ventana efectiva, capacidad y conflictos de una fecha propuesta, sin cambiar la orden */
    void verificarFecha(Integer idOrden, Instant inicio);

    /** RF48: aplica la fecha propuesta aceptada por el administrador, verificándola de nuevo */
    void reprogramarEnAutorizacion(Integer idOrden, Instant inicio, String motivo, Actor actor);
}
