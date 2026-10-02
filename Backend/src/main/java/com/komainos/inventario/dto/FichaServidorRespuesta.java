package com.komainos.inventario.dto;

import com.komainos.inventario.dto.ServidorResumenRespuesta.CriticidadResumen;
import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.shared.dto.ReferenciaSimple;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record FichaServidorRespuesta(
        Integer id,
        String hostname,
        /** IP principal */
        String direccionIp,
        List<DireccionIpRespuesta> direccionesIp,
        String vdc,
        String servidorFisico,
        String vlan,
        String cluster,
        String dns,
        String plataforma,
        String descripcion,
        Integer cantidadCpu,
        BigDecimal ramGb,
        BigDecimal hdVirtualGb,
        ReferenciaSimple versionSistemaOperativo,
        ReferenciaSimple sistemaOperativo,
        FamiliaSistemaOperativo familiaSistemaOperativo,
        ReferenciaSimple entorno,
        CriticidadResumen criticidad,
        ReferenciaSimple responsable,
        EstadoServidor estado,
        Instant fechaAlta,
        Instant fechaActualizacion,
        ConfiguracionRespuesta configuracion,
        List<VentanaRespuesta> ventanas,
        List<ReferenciaSimple> grupos,
        SolicitudBajaRespuesta bajaPendiente,
        List<SolicitudBajaRespuesta> historialBajas,
        List<ReactivacionRespuesta> reactivaciones) {
}
