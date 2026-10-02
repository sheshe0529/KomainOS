package com.komainos.inventario.dto;

import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Instant;

public record ReactivacionRespuesta(Instant fecha, ReferenciaSimple usuario) {
}
