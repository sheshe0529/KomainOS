package com.komainos.catalogo.model;

/** Ordenado de menor a mayor impacto: la afectación de una orden es la mayor entre sus tareas (RF45) */
public enum NivelAfectacion {
    SIN_AFECTACION,
    PARCIAL,
    TOTAL;

    public NivelAfectacion mayor(NivelAfectacion otro) {
        return ordinal() >= otro.ordinal() ? this : otro;
    }
}
