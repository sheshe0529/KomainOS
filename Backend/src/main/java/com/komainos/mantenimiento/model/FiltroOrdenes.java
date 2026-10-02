package com.komainos.mantenimiento.model;

import java.time.Instant;

public record FiltroOrdenes(String codigo, Integer idServidor, Integer idGrupo, EstadoOrden estado,
                            Integer idNivelCriticidad, Instant desde, Instant hasta) {
}
