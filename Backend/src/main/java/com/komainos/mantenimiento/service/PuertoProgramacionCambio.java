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

    /**
     * RF48, CP122: comprueba capacidad y conflictos de una fecha propuesta, sin cambiar la orden. Fuera de la ventana
     * efectiva solo la admite con la confirmación expresa de quien la propone.
     *
     * @return si la fecha cae dentro de la ventana permisiva
     */
    boolean verificarFecha(Integer idOrden, Instant inicio, boolean confirmaFueraDeVentana);

    /**
     * RF48: aplica la fecha propuesta aceptada por el administrador, verificándola de nuevo. Fuera de la ventana exige
     * que la excepción esté confirmada (confirmacion_fuera_ventana)
     */
    void reprogramarEnAutorizacion(Integer idOrden, Instant inicio, String motivo, Actor actor);
}
