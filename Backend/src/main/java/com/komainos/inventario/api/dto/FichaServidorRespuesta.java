package com.komainos.inventario.api.dto;

import com.komainos.inventario.api.dto.ServidorResumenRespuesta.CriticidadResumen;
import com.komainos.inventario.dominio.EstadoServidor;
import com.komainos.inventario.dominio.FamiliaSistemaOperativo;
import com.komainos.shared.api.ReferenciaSimple;

import java.time.Instant;
import java.util.List;

/**
 * Ficha del servidor (RF14, HU10). Las ordenes del servidor se consultan en
 * {@code /api/ordenes?idServidor=}; metricas, incidencias y cuentas de
 * servicio pertenecen a iteraciones posteriores.
 */
public record FichaServidorRespuesta(
        Integer id,
        String hostname,
        String direccionIp,
        String datacenter,
        String servidorFisico,
        String vlan,
        String cluster,
        String dns,
        String plataforma,
        String descripcion,
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
