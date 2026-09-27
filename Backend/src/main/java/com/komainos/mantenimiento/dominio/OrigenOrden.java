package com.komainos.mantenimiento.dominio;

/** Origen de una orden ({@code enum_origen_orden}). */
public enum OrigenOrden {

    /** Generada por el Sistema en modalidad automatica (RF27). */
    PLANIFICACION_AUTOMATICA,

    /** Programada por el administrador (RF30, RF70). */
    SOLICITUD_BAJO_DEMANDA,

    /** Lanzamiento inmediato (RF31), iteracion posterior. */
    LANZAMIENTO_INMEDIATO,

    /** Activada por una condicion critica (RF40), iteracion posterior. */
    CONDICION_CRITICA;

    /**
     * Prioridad de la orden, atributo derivado no persistido: los lanzamientos
     * inmediatos tienen prioridad alta (RF31) y las condiciones criticas se
     * tratan igual por su urgencia.
     */
    public PrioridadOrden prioridad() {
        return this == LANZAMIENTO_INMEDIATO || this == CONDICION_CRITICA
                ? PrioridadOrden.ALTA
                : PrioridadOrden.NORMAL;
    }
}
