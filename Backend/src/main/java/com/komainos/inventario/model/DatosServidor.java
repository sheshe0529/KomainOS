package com.komainos.inventario.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Datos en términos de dominio para que la importación reutilice el caso de uso sin un DTO HTTP */
public record DatosServidor(
        String hostname,
        /** IP principal */
        String direccionIp,
        /** Virtual DataCenter (antes datacenter) */
        String vdc,
        String servidorFisico,
        String vlan,
        String cluster,
        String dns,
        Integer idVersionSistemaOperativo,
        String plataforma,
        Integer idEntorno,
        Integer idNivelCriticidad,
        Integer idResponsable,
        String descripcion,
        List<String> direccionesIpAdicionales,
        Integer cantidadCpu,
        BigDecimal ramGb,
        BigDecimal hdVirtualGb) {

    public DatosServidor {
        direccionesIpAdicionales = direccionesIpAdicionales == null ? List.of() : List.copyOf(direccionesIpAdicionales);
    }

    public List<String> todasLasDirecciones() {
        List<String> todas = new ArrayList<>();
        todas.add(direccionIp);
        todas.addAll(direccionesIpAdicionales);
        return todas;
    }
}
