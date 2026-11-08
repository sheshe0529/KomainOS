package com.komainos.mantenimiento.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Por qué se cerró una orden sin completar el ciclo. Los estados que lo registran (tabla 6) pasan a CERRADA en la misma
 * operación, así que el filtro por estado actual no los encuentra: se buscan en el historial de la orden.
 */
public enum MotivoCierre {

    /** Rechazada o con el plazo de autorización vencido, como agrupa la bitácora: no se ejecutó */
    NO_AUTORIZADO(EnumSet.of(EstadoOrden.RECHAZADA, EstadoOrden.AUTORIZACION_VENCIDA)),
    /** Se ejecutó, pero el responsable no dio su conformidad a tiempo (RF57) */
    VALIDACION_VENCIDA(EnumSet.of(EstadoOrden.VALIDACION_VENCIDA));

    private final Set<EstadoOrden> estados;

    MotivoCierre(Set<EstadoOrden> estados) {
        this.estados = estados;
    }

    /** Los estados del historial que indican este motivo */
    public Set<EstadoOrden> estados() {
        return estados;
    }
}
