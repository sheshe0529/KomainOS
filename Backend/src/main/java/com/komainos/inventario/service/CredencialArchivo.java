package com.komainos.inventario.service;

/**
 * Credencial principal tal como viaja en el archivo del inventario: textos de las celdas, los secretos sin recortar.
 * El método de elevación admite también los valores anteriores al DEC-57 (Administrador y Genérico).
 */
public record CredencialArchivo(String mecanismo, String metodoElevacion, String usuario, String secreto,
                                String secretoElevacion) {

    @Override
    public String toString() {
        return "CredencialArchivo[protegida]";
    }
}
