package com.komainos.mantenimiento.model;

import java.time.Instant;

/** @param motivoCierre solo órdenes cerradas por ese motivo (DEC-61); nulo para no filtrar */
public record FiltroOrdenes(String codigo, Integer idServidor, Integer idGrupo, EstadoOrden estado,
                            Integer idNivelCriticidad, Instant desde, Instant hasta, MotivoCierre motivoCierre) {
}
