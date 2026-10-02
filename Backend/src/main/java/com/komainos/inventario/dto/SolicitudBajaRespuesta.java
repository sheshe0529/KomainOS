package com.komainos.inventario.dto;

import com.komainos.inventario.model.EstadoSolicitudBaja;
import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Instant;

public record SolicitudBajaRespuesta(Integer id, EstadoSolicitudBaja estado, String motivo,
                                     ReferenciaSimple solicitante, Instant fechaSolicitud,
                                     Instant fechaAplicacion) {
}
