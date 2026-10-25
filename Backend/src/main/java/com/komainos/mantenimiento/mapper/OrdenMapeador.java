package com.komainos.mantenimiento.mapper;

import com.komainos.inventario.dto.ServidorResumenRespuesta.CriticidadResumen;
import com.komainos.mantenimiento.dto.OrdenDetalleRespuesta;
import com.komainos.mantenimiento.dto.OrdenResumenRespuesta;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.service.ServicioConsultaOrdenes.DetalleOrden;
import com.komainos.seguridad.model.Usuario;
import com.komainos.shared.dto.ReferenciaSimple;

/** Espera las colecciones ya cargadas por el servicio: open-in-view está desactivado */
public final class OrdenMapeador {

    private OrdenMapeador() {
    }

    public static OrdenResumenRespuesta resumen(Orden o) {
        ReferenciaSimple objetivo = o.esGrupal()
                ? new ReferenciaSimple(o.getGrupo().getId(), o.getGrupo().getNombre())
                : new ReferenciaSimple(o.getServidor().getId(), o.getServidor().getHostname());
        int cantidad = Math.max(1, (int) o.getDetalles().stream().filter(d -> d.getEstado().ocupaServidor()).count());
        return new OrdenResumenRespuesta(
                o.getId(), o.getCodigo(), o.esGrupal() ? "GRUPAL" : "INDIVIDUAL", objetivo, cantidad,
                new CriticidadResumen(o.getNivelCriticidad().getId(), o.getNivelCriticidad().getNombre(),
                        o.getNivelCriticidad().getPrioridad()),
                o.getOrigen(), o.prioridad(), o.getEstado(), o.getEstado().etapa(),
                o.getFechaObjetivo(),
                o.getFechaInicioProgramada(),
                o.getFechaFinProgramada(),
                o.getFechaEvaluacionProgramada(),
                o.getFechaCreacion());
    }

    public static OrdenDetalleRespuesta detalle(DetalleOrden detalle) {
        Orden o = detalle.orden();
        return new OrdenDetalleRespuesta(
                resumen(o),
                usuario(o.getUsuarioSolicitante()),
                o.getModoEjecucionAplicado(),
                detalle.cuentaServicio().map(nombre -> new ReferenciaSimple(o.getIdCuentaServicio(), nombre)).orElse(null),
                o.getInicioVentanaAplicada(),
                o.getFinVentanaAplicada(),
                detalle.detalles().stream()
                        .map(d -> new OrdenDetalleRespuesta.Detalle(d.getId(),
                                new ReferenciaSimple(d.getServidor().getId(), d.getServidor().getHostname()),
                                d.getServidor().getDireccionIp(), d.getPosicionEjecucion(), d.isEsServidorPiloto(),
                                d.getEstado(), d.getFechaPrevistaInicio(), d.getFechaPrevistaFin(),
                                d.getFechaRealInicio(), d.getFechaRealFin()))
                        .toList(),
                o.getHistorial().stream()
                        .map(h -> new OrdenDetalleRespuesta.CambioEstado(h.getEstadoAnterior(), h.getEstadoNuevo(),
                                h.getMotivo(), usuario(h.getUsuario()), h.getFechaHora()))
                        .toList());
    }

    private static ReferenciaSimple usuario(Usuario u) {
        return u == null ? null : new ReferenciaSimple(u.getId(), u.getNombreCompleto());
    }
}
