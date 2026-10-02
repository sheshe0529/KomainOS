package com.komainos.inventario.dto;

import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.shared.dto.ReferenciaSimple;

import java.math.BigDecimal;
import java.time.Instant;

public record ServidorResumenRespuesta(
        Integer id,
        String hostname,
        /** IP principal */
        String direccionIp,
        int cantidadDireccionesIp,
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
        ReferenciaSimple sistemaOperativo,
        ReferenciaSimple versionSistemaOperativo,
        FamiliaSistemaOperativo familiaSistemaOperativo,
        ReferenciaSimple entorno,
        CriticidadResumen criticidad,
        ReferenciaSimple responsable,
        EstadoServidor estado,
        Instant fechaAlta,
        Instant fechaActualizacion) {

    public record CriticidadResumen(Integer id, String nombre, Integer prioridad) {
    }
}
