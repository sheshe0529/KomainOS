package com.komainos.mantenimiento.repository;

import java.time.Instant;

public record FilaReserva(Integer idServidor, Instant inicio, Instant fin, Integer idOrden, String codigoOrden) {
}
