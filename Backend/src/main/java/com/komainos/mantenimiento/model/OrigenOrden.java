package com.komainos.mantenimiento.model;

public enum OrigenOrden {

    PLANIFICACION_AUTOMATICA,

    SOLICITUD_BAJO_DEMANDA,

    LANZAMIENTO_INMEDIATO,

    CONDICION_CRITICA;

    /** Derivada, no se persiste: lanzamiento inmediato y condición crítica son de prioridad alta */
    public PrioridadOrden prioridad() {
        return this == LANZAMIENTO_INMEDIATO || this == CONDICION_CRITICA
                ? PrioridadOrden.ALTA
                : PrioridadOrden.NORMAL;
    }
}
