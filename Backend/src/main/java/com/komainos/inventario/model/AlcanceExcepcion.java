package com.komainos.inventario.model;

/**
 * Dimensión de una excepción de umbral (ck_umbral_excepcion_alcance: una sola por excepción). El orden de declaración
 * es la prioridad al resolver: servidor, grupo, entorno, criticidad y sistema operativo, y sin excepción se aplica el
 * umbral general (R2.4). El sistema operativo va al final: adapta el umbral a la plataforma (por ejemplo, los servicios
 * requeridos de Windows) y las demás dimensiones lo refinan.
 */
public enum AlcanceExcepcion {

    SERVIDOR("el servidor"),
    GRUPO("el grupo de mantenimiento"),
    ENTORNO("el entorno"),
    CRITICIDAD("el nivel de criticidad"),
    SISTEMA_OPERATIVO("el sistema operativo");

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
