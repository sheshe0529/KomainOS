package com.komainos.mantenimiento.mapper;

import com.komainos.inventario.dto.ServidorResumenRespuesta.CriticidadResumen;
import com.komainos.mantenimiento.dto.OrdenDetalleRespuesta;
import com.komainos.mantenimiento.dto.OrdenResumenRespuesta;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.ProgramacionOrden;
import com.komainos.mantenimiento.service.ServicioConsultaOrdenes.DetalleOrden;
import com.komainos.seguridad.model.Usuario;
import com.komainos.shared.dto.ReferenciaSimple;

import java.util.Comparator;
import java.util.Optional;

/** Espera las colecciones ya cargadas por el servicio: open-in-view está desactivado */
public final class OrdenMapeador {

    private OrdenMapeador() {
    }

    public static OrdenResumenRespuesta resumen(Orden o) {
        return resumen(o, o.programacionVigente());
    }

    public static OrdenResumenRespuesta resumen(Orden o, Optional<ProgramacionOrden> vigente) {
        ReferenciaSimple objetivo = o.esGrupal()
                ? new ReferenciaSimple(o.getGrupo().getId(), o.getGrupo().getNombre())
                : new ReferenciaSimple(o.getServidor().getId(), o.getServidor().getHostname());
        int cantidad = Math.max(1, (int) o.getDetalles().stream().filter(d -> d.getEstado().ocupaServidor()).count());
        return new OrdenResumenRespuesta(
                o.getId(), o.getCodigo(), o.esGrupal() ? "GRUPAL" : "INDIVIDUAL", objetivo, cantidad,
                new CriticidadResumen(o.getNivelCriticidad().getId(), o.getNivelCriticidad().getNombre(),
                        o.getNivelCriticidad().getPrioridad()),
                o.getOrigen(), o.prioridad(), o.getEstado(), o.getEstado().etapa(),
                vigente.map(ProgramacionOrden::getFechaObjetivo).orElse(null),
                vigente.map(ProgramacionOrden::getFechaInicioProgramada).orElse(null),
                vigente.map(ProgramacionOrden::getFechaFinProgramada).orElse(null),
                vigente.map(ProgramacionOrden::getFechaEvaluacionProgramada).orElse(null),
                o.getFechaCreacion());
    }

    public static OrdenDetalleRespuesta detalle(DetalleOrden detalle) {
        Orden o = detalle.orden();
        Optional<ProgramacionOrden> vigente = o.programacionVigente();
        return new OrdenDetalleRespuesta(
                resumen(o, vigente),
                usuario(o.getUsuarioSolicitante()),
                o.getModoEjecucionAplicado(),
                detalle.cuentaServicio().map(nombre -> new ReferenciaSimple(o.getIdCuentaServicio(), nombre)).orElse(null),
                vigente.map(ProgramacionOrden::getInicioVentanaAplicada).orElse(null),
                vigente.map(ProgramacionOrden::getFinVentanaAplicada).orElse(null),
                o.getDetalles().stream()
                        .map(d -> new OrdenDetalleRespuesta.Detalle(d.getId(),
                                new ReferenciaSimple(d.getServidor().getId(), d.getServidor().getHostname()),
                                d.getServidor().getDireccionIp(), d.getPosicionEjecucion(), d.isEsServidorPiloto(),
                                d.getEstado(), d.getFechaPrevistaInicio(), d.getFechaPrevistaFin(),
                                d.getFechaRealInicio(), d.getFechaRealFin()))
                        .toList(),
                o.getProgramaciones().stream()
                        .sorted(Comparator.comparingInt(ProgramacionOrden::getNumeroVersion).reversed())
                        .map(p -> new OrdenDetalleRespuesta.Programacion(p.getNumeroVersion(), p.getFechaObjetivo(),
                                p.getFechaInicioProgramada(), p.getFechaFinProgramada(),
                                p.getFechaEvaluacionProgramada(), p.getInicioVentanaAplicada(),
                                p.getFinVentanaAplicada(), p.getMotivo(), usuario(p.getUsuarioRegistro()),
                                p.getFechaRegistro()))
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
