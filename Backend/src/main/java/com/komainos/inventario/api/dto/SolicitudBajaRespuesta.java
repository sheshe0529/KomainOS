package com.komainos.inventario.api.dto;

import com.komainos.inventario.dominio.EstadoSolicitudBaja;

import java.time.Instant;

public record SolicitudBajaRespuesta(Integer id, EstadoSolicitudBaja estado, String motivo,
                                     Instant fechaSolicitud, Instant fechaAplicacion) {
}
