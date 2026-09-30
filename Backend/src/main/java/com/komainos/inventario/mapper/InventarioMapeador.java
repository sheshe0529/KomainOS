package com.komainos.inventario.mapper;

import com.komainos.inventario.dto.ConfiguracionRespuesta;
import com.komainos.inventario.dto.FichaGrupoRespuesta;
import com.komainos.inventario.dto.DireccionIpRespuesta;
import com.komainos.inventario.dto.FichaServidorRespuesta;
import com.komainos.inventario.dto.GrupoResumenRespuesta;
import com.komainos.inventario.dto.ReactivacionRespuesta;
import com.komainos.inventario.dto.ServidorResumenRespuesta.CriticidadResumen;
import com.komainos.inventario.dto.ServidorResumenRespuesta;
import com.komainos.inventario.dto.SolicitudBajaRespuesta;
import com.komainos.inventario.dto.VentanaRespuesta;
import com.komainos.inventario.model.CalendarioSemanal.IntervaloSemanal;
import com.komainos.inventario.model.CalendarioSemanal;
import com.komainos.inventario.model.ConfiguracionGrupo;
import com.komainos.inventario.model.ConfiguracionMantenimiento;
import com.komainos.inventario.model.GrupoMantenimiento;
import com.komainos.inventario.model.NivelCriticidad;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.model.SolicitudBaja;
import com.komainos.inventario.model.VentanaMantenimiento;
import com.komainos.inventario.model.VersionSistemaOperativo;
import com.komainos.inventario.service.ServicioGrupo.FichaGrupo;
import com.komainos.inventario.service.ServicioServidor.FichaServidor;
import com.komainos.shared.dto.ReferenciaSimple;

import java.util.Comparator;
import java.util.List;

/**
 * Traduccion del dominio del inventario al contrato HTTP.
 *
 * <p>Se escribe a mano: es el unico punto donde se decide que sale del
 * sistema. Lee asociaciones, asi que las entidades deben venir con su
 * {@code @EntityGraph} cargado desde el servicio.
 */
public final class InventarioMapeador {

    private InventarioMapeador() {
    }

    public static ServidorResumenRespuesta resumen(Servidor s) {
        VersionSistemaOperativo version = s.getVersionSistemaOperativo();
        return new ServidorResumenRespuesta(
                s.getId(), s.getHostname(), s.getDireccionIp(), s.getCantidadDirecciones(), s.getVdc(),
                s.getServidorFisico(), s.getVlan(), s.getCluster(), s.getDns(), s.getPlataforma(), s.getDescripcion(),
                s.getCantidadCpu(), s.getRamGb(), s.getHdVirtualGb(),
                new ReferenciaSimple(version.getSistemaOperativo().getId(), version.getSistemaOperativo().getNombre()),
                new ReferenciaSimple(version.getId(), version.descripcionCompleta()),
                version.getSistemaOperativo().getFamilia(),
                new ReferenciaSimple(s.getEntorno().getId(), s.getEntorno().getNombre()),
                criticidad(s.getNivelCriticidad()),
                new ReferenciaSimple(s.getResponsable().getId(), s.getResponsable().getNombreCompleto()),
                s.getEstado(),
                s.getFechaAlta(),
                s.getFechaActualizacion());
    }

    public static FichaServidorRespuesta ficha(FichaServidor ficha) {
        Servidor s = ficha.servidor();
        VersionSistemaOperativo version = s.getVersionSistemaOperativo();
        return new FichaServidorRespuesta(
                s.getId(), s.getHostname(), s.getDireccionIp(),
                s.direccionesOrdenadas().stream()
                        .map(d -> new DireccionIpRespuesta(d.getId(), d.getDireccion(), d.isPrincipal()))
                        .toList(),
                s.getVdc(), s.getServidorFisico(), s.getVlan(), s.getCluster(), s.getDns(), s.getPlataforma(),
                s.getDescripcion(), s.getCantidadCpu(), s.getRamGb(), s.getHdVirtualGb(),
                new ReferenciaSimple(version.getId(), version.getVersion()),
                new ReferenciaSimple(version.getSistemaOperativo().getId(), version.getSistemaOperativo().getNombre()),
                version.getSistemaOperativo().getFamilia(),
                new ReferenciaSimple(s.getEntorno().getId(), s.getEntorno().getNombre()),
                criticidad(s.getNivelCriticidad()),
                new ReferenciaSimple(s.getResponsable().getId(), s.getResponsable().getNombreCompleto()),
                s.getEstado(),
                s.getFechaAlta(),
                s.getFechaActualizacion(),
                ficha.configuracion().map(InventarioMapeador::configuracion).orElse(null),
                ventanas(s.getVentanas()),
                ficha.grupos().stream().map(g -> new ReferenciaSimple(g.getId(), g.getNombre())).toList(),
                ficha.bajaPendiente().map(InventarioMapeador::solicitud).orElse(null),
                ficha.bajas().stream().map(InventarioMapeador::solicitud).toList(),
                ficha.reactivaciones().stream()
                        .map(r -> new ReactivacionRespuesta(r.fecha(), r.usuario() == null ? null
                                : new ReferenciaSimple(r.usuario().getId(), r.usuario().getNombreCompleto())))
                        .toList());
    }

    public static ConfiguracionRespuesta configuracion(ConfiguracionMantenimiento c) {
        return new ConfiguracionRespuesta(
                c.getFrecuenciaRevisionDias(), c.getFrecuenciaMantenimientoDias(), c.getModalidadPlanificacion(),
                c instanceof ConfiguracionGrupo g ? g.getModoEjecucion() : null,
                c.getIdCuentaServicio(), c.getIdCuentaServicio() == null,
                c.getFechaCreacion(), c.getFechaActualizacion());
    }

    public static List<VentanaRespuesta> ventanas(java.util.Collection<VentanaMantenimiento> ventanas) {
        return ventanas.stream()
                .sorted(Comparator.comparing(VentanaMantenimiento::getDiaInicio)
                        .thenComparing(VentanaMantenimiento::getHoraInicio))
                .map(v -> new VentanaRespuesta(v.getDiaInicio(), v.getHoraInicio(), v.getDiaFin(), v.getHoraFin(),
                        v.duracion().toMinutes()))
                .toList();
    }

    public static List<VentanaRespuesta> intervalos(List<IntervaloSemanal> intervalos) {
        return intervalos.stream()
                .map(i -> new VentanaRespuesta(i.diaInicio(), i.horaInicio(), i.diaFin(), i.horaFin(),
                        duracionMinutos(i)))
                .toList();
    }

    public static SolicitudBajaRespuesta solicitud(SolicitudBaja s) {
        return new SolicitudBajaRespuesta(s.getId(), s.getEstado(), s.getMotivo(),
                new ReferenciaSimple(s.getSolicitante().getId(), s.getSolicitante().getNombreCompleto()),
                s.getFechaSolicitud(), s.getFechaAplicacion());
    }

    public static GrupoResumenRespuesta resumen(FichaGrupo ficha) {
        GrupoMantenimiento g = ficha.grupo();
        Servidor referencia = g.servidores().isEmpty() ? null : g.servidores().getFirst();
        return new GrupoResumenRespuesta(
                g.getId(), g.getNombre(), g.getDescripcion(), g.getEstado(), g.getIntegrantes().size(),
                ficha.criticidadEfectiva().map(InventarioMapeador::criticidad).orElse(null),
                referencia == null ? null : new ReferenciaSimple(referencia.getEntorno().getId(),
                        referencia.getEntorno().getNombre()),
                referencia == null ? null : new ReferenciaSimple(referencia.getResponsable().getId(),
                        referencia.getResponsable().getNombreCompleto()),
                referencia == null ? null : sistemaOperativo(referencia),
                ficha.configuracion().map(ConfiguracionMantenimiento::getModalidadPlanificacion).orElse(null),
                ficha.configuracion().map(ConfiguracionGrupo::getModoEjecucion).orElse(null));
    }

    public static FichaGrupoRespuesta ficha(FichaGrupo ficha) {
        GrupoMantenimiento g = ficha.grupo();
        List<Servidor> servidores = g.servidores().stream()
                .sorted(Comparator.comparing(Servidor::getHostname)).toList();
        Servidor referencia = servidores.isEmpty() ? null : servidores.getFirst();
        return new FichaGrupoRespuesta(
                g.getId(), g.getNombre(), g.getDescripcion(), g.getEstado(),
                ficha.criticidadEfectiva().map(InventarioMapeador::criticidad).orElse(null),
                referencia == null ? null : new ReferenciaSimple(referencia.getEntorno().getId(),
                        referencia.getEntorno().getNombre()),
                referencia == null ? null : new ReferenciaSimple(referencia.getResponsable().getId(),
                        referencia.getResponsable().getNombreCompleto()),
                referencia == null ? null : sistemaOperativo(referencia),
                ficha.configuracion().map(InventarioMapeador::configuracion).orElse(null),
                servidores.stream().map(InventarioMapeador::resumen).toList(),
                ficha.ventanaEfectiva() == null ? List.of() : intervalos(ficha.ventanaEfectiva()),
                g.getFechaCreacion(), g.getFechaActualizacion());
    }

    private static ReferenciaSimple sistemaOperativo(Servidor s) {
        var so = s.getVersionSistemaOperativo().getSistemaOperativo();
        return new ReferenciaSimple(so.getId(), so.getNombre());
    }

    private static CriticidadResumen criticidad(NivelCriticidad n) {
        return new CriticidadResumen(n.getId(), n.getNombre(), n.getPrioridad());
    }

    private static long duracionMinutos(IntervaloSemanal i) {
        long minutos = (long) i.diaInicio().diasHasta(i.diaFin()) * CalendarioSemanal.MINUTOS_DIA
                + (i.horaFin().toSecondOfDay() - i.horaInicio().toSecondOfDay()) / 60;
        return minutos <= 0 ? minutos + CalendarioSemanal.MINUTOS_SEMANA : minutos;
    }
}
