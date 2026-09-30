package com.komainos.mantenimiento.model;

import java.time.Instant;

/**
 * Criterios de consulta de ordenes (RF36, HU23). Cada campo es opcional.
 *
 * @param codigo coincidencia parcial del codigo de la orden
 * @param desde  inicio programado vigente a partir de este instante
 * @param hasta  inicio programado vigente antes de este instante
 */
public record FiltroOrdenes(String codigo, Integer idServidor, Integer idGrupo, EstadoOrden estado,
                            Integer idNivelCriticidad, Instant desde, Instant hasta) {
}
