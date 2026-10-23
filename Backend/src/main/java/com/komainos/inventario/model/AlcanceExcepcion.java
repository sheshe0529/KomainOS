package com.komainos.inventario.model;

/**
 * Dimensión de una excepción de umbral (ck_umbral_excepcion_alcance: una sola por excepción). El orden de declaración
 * es la prioridad al resolver: servidor, grupo, entorno y criticidad, y sin excepción se aplica el umbral general (R2.4).
 */
public enum AlcanceExcepcion {

    SERVIDOR("el servidor"),
    GRUPO("el grupo de mantenimiento"),
    ENTORNO("el entorno"),
    CRITICIDAD("el nivel de criticidad");

    private final String descripcion;

    AlcanceExcepcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String descripcion() {
        return descripcion;
    }

    public OrigenUmbral comoOrigen() {
        return OrigenUmbral.valueOf(name());
    }
}
