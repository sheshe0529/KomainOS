package com.komainos.inventario.model;

/**
 * Datos de alta o edicion de un servidor (RF10), en terminos de dominio.
 *
 * <p>Existe para que el servicio no reciba el DTO de la API: el dia que la
 * importacion masiva (RF12) alimente el mismo caso de uso, lo reutiliza sin
 * fabricar una peticion HTTP. Llega ya validado en formato.
 */
public record DatosServidor(
        String hostname,
        String direccionIp,
        String datacenter,
        String servidorFisico,
        String vlan,
        String cluster,
        String dns,
        Integer idVersionSistemaOperativo,
        String plataforma,
        Integer idEntorno,
        Integer idNivelCriticidad,
        Integer idResponsable,
        String descripcion) {
}
