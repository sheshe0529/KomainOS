package com.komainos.inventario.model;

/**
 * Criterios de busqueda del inventario (RF11, HU07). Cada campo es opcional.
 *
 * @param texto coincidencia parcial contra hostname, cualquiera de sus IP, DNS, VDC o
 *              nombre del responsable (HU07 CA3 y pantalla preliminar)
 */
public record FiltroServidores(
        String texto,
        EstadoServidor estado,
        Integer idEntorno,
        Integer idNivelCriticidad,
        Integer idSistemaOperativo,
        Integer idResponsable,
        String vdc) {

    public static FiltroServidores vacio() {
        return new FiltroServidores(null, null, null, null, null, null, null);
    }
}
