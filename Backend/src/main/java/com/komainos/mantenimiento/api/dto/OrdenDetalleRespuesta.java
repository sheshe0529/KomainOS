package com.komainos.mantenimiento.api.dto;

import com.komainos.inventario.dominio.ModoEjecucion;
import com.komainos.mantenimiento.dominio.EstadoDetalleOrden;
import com.komainos.mantenimiento.dominio.EstadoOrden;
import com.komainos.shared.api.ReferenciaSimple;

import java.time.Instant;
import java.util.List;

/**
 * Detalle de una orden (RF33): informacion general y, por cada servidor, su
 * estado, posicion de ejecucion y fechas previstas y reales; ademas el
 * historial de programaciones (RF30) y de estados.
 */
public record OrdenDetalleRespuesta(
        OrdenResumenRespuesta resumen,
        ReferenciaSimple solicitante,
        ModoEjecucion modoEjecucionAplicado,
        boolean usaCuentaPredeterminada,
        Instant inicioVentanaAplicada,
        Instant finVentanaAplicada,
        List<Detalle> detalles,
        List<Programacion> programaciones,
        List<CambioEstado> historial) {

    public record Detalle(Integer id, ReferenciaSimple servidor, String direccionIp, int posicionEjecucion,
                          boolean esServidorPiloto, EstadoDetalleOrden estado, Instant fechaPrevistaInicio,
                          Instant fechaPrevistaFin, Instant fechaRealInicio, Instant fechaRealFin) {
    }

    public record Programacion(int numeroVersion, Instant fechaObjetivo, Instant inicio, Instant fin,
                               Instant fechaEvaluacion, Instant inicioVentana, Instant finVentana, String motivo,
                               ReferenciaSimple registradoPor, Instant fechaRegistro) {
    }

    /** {@code usuario} nulo significa que la transicion la hizo el Sistema. */
    public record CambioEstado(EstadoOrden estadoAnterior, EstadoOrden estadoNuevo, String motivo,
                               ReferenciaSimple usuario, Instant fechaHora) {
    }
}
