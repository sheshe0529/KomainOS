package com.komainos.inventario.dto;

import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Instant;

/** Reactivación de un servidor dado de baja (RF73); {@code usuario} es nulo si la hizo el sistema. */
public record ReactivacionRespuesta(Instant fecha, ReferenciaSimple usuario) {
}
