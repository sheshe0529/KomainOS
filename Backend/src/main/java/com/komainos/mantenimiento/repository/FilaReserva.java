package com.komainos.mantenimiento.repository;

import java.time.Instant;

/** Fila plana de una reserva del cronograma; la planificacion la convierte en la entrada R del algoritmo. */
public record FilaReserva(Integer idServidor, Instant inicio, Instant fin, Integer idOrden, String codigoOrden) {
}
