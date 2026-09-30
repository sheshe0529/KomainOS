package com.komainos.inventario.dto;

import com.komainos.inventario.model.EstadoSolicitudBaja;
import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Instant;

/**
 * Solicitud de baja de un servidor (RF72).
 *
 * @param solicitante     administrador que la solicitó
 * @param fechaAplicacion cuándo se aplicó; nula mientras está pendiente
 */
public record SolicitudBajaRespuesta(Integer id, EstadoSolicitudBaja estado, String motivo,
                                     ReferenciaSimple solicitante, Instant fechaSolicitud,
                                     Instant fechaAplicacion) {
}
