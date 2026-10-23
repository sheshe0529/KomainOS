package com.komainos.mantenimiento.dto;

import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Instant;
import java.util.List;

public record OrdenDetalleRespuesta(
        OrdenResumenRespuesta resumen,
        ReferenciaSimple solicitante,
        ModoEjecucion modoEjecucionAplicado,
        /** Cuenta resuelta al generar la orden, nula si no había ninguna asignada ni predeterminada */
        ReferenciaSimple cuentaServicio,
        Instant inicioVentanaAplicada,
        Instant finVentanaAplicada,
        List<Detalle> detalles,
        /** Incluye las reprogramaciones: la orden guarda solo su programación vigente (R2.4) */
        List<CambioEstado> historial) {

    public record Detalle(Integer id, ReferenciaSimple servidor, String direccionIp, int posicionEjecucion,
                          boolean esServidorPiloto, EstadoDetalleOrden estado, Instant fechaPrevistaInicio,
                          Instant fechaPrevistaFin, Instant fechaRealInicio, Instant fechaRealFin) {
    }

    /** usuario nulo: la transición la hizo el Sistema */
    public record CambioEstado(EstadoOrden estadoAnterior, EstadoOrden estadoNuevo, String motivo,
                               ReferenciaSimple usuario, Instant fechaHora) {
    }
}
