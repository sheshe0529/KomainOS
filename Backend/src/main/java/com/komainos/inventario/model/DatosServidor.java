package com.komainos.inventario.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos de alta o edicion de un servidor (RF10), en terminos de dominio.
 *
 * <p>Existe para que el servicio no reciba el DTO de la API: el dia que la
 * importacion masiva (RF12) alimente el mismo caso de uso, lo reutiliza sin
 * fabricar una peticion HTTP. Llega ya validado en formato.
 */
public record DatosServidor(
        String hostname,
        /** IP principal (DEC-37). */
        String direccionIp,
        /** Virtual DataCenter (DEC-37, antes datacenter). */
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
        /** IP que no son la principal; vacia si solo tiene una. */
        List<String> direccionesIpAdicionales,
        Integer cantidadCpu,
        BigDecimal ramGb,
        BigDecimal hdVirtualGb) {

    public DatosServidor {
        direccionesIpAdicionales = direccionesIpAdicionales == null ? List.of() : List.copyOf(direccionesIpAdicionales);
    }

    /** La principal seguida de las adicionales. */
    public List<String> todasLasDirecciones() {
        List<String> todas = new ArrayList<>();
        todas.add(direccionIp);
        todas.addAll(direccionesIpAdicionales);
        return todas;
    }
}
