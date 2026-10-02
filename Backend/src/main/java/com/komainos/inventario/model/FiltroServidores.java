package com.komainos.inventario.model;

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
