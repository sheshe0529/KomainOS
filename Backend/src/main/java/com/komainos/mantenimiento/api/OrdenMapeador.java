package com.komainos.mantenimiento.api;

import com.komainos.inventario.api.dto.ServidorResumenRespuesta.CriticidadResumen;
import com.komainos.mantenimiento.api.dto.OrdenDetalleRespuesta;
import com.komainos.mantenimiento.api.dto.OrdenResumenRespuesta;
import com.komainos.mantenimiento.dominio.Orden;
import com.komainos.mantenimiento.dominio.ProgramacionOrden;
import com.komainos.seguridad.dominio.Usuario;
import com.komainos.shared.api.ReferenciaSimple;

import java.util.Comparator;
import java.util.Optional;

/**
 * Traduccion de ordenes al contrato HTTP. Espera las colecciones ya cargadas
 * por el servicio (open-in-view desactivado).
 */
public final class OrdenMapeador {

    private OrdenMapeador() {
    }

    public static OrdenResumenRespuesta resumen(Orden o) {
        return resumen(o, o.programacionVigente());
    }

    /** Variante para el cronograma, que ya trae la programacion vigente. */
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

    public static OrdenDetalleRespuesta detalle(Orden o) {
        Optional<ProgramacionOrden> vigente = o.programacionVigente();
        return new OrdenDetalleRespuesta(
                resumen(o, vigente),
                usuario(o.getUsuarioSolicitante()),
                o.getModoEjecucionAplicado(),
                o.getIdCuentaServicio() == null,
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
