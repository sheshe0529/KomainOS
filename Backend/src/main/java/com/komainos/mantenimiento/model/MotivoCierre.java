package com.komainos.mantenimiento.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Por qué terminó el ciclo de una orden (DEC-61). Solo una orden CERRADA tiene motivo (ck_orden_cierre) y cada motivo
 * se admite únicamente desde los estados en que puede ocurrir: así el estado dice dónde está la orden y el motivo, cómo
 * terminó, sin reconstruirlo desde el historial.
 */
public enum MotivoCierre {

    /** Validaciones técnica y funcional conformes (RF55, RF57) */
    EXITOSO("Exitoso", EnumSet.of(EstadoOrden.PENDIENTE_VALIDACION_FUNCIONAL)),
    /** La evaluación previa no activó ninguna tarea: las métricas están dentro de los umbrales (RF39) */
    SIN_INTERVENCION("Sin intervención", EnumSet.of(EstadoOrden.EN_EVALUACION)),
    /** Alguna tarea activada no tiene implementación compatible o falta el análisis: se bloquea y se avisa (RF42) */
    SIN_IMPLEMENTACION("Sin implementación", EnumSet.of(EstadoOrden.EN_EVALUACION)),
    /** Venció el plazo compartido sin las aprobaciones requeridas (RF50, EA05) */
    AUTORIZACION_VENCIDA("Autorización vencida", EnumSet.of(EstadoOrden.PENDIENTE_AUTORIZACION)),
    /** El responsable no respondió la validación funcional a tiempo; no se presume conformidad (RF57, EA06) */
    VALIDACION_VENCIDA("Validación vencida", EnumSet.of(EstadoOrden.PENDIENTE_VALIDACION_FUNCIONAL)),
    /** Programación cancelada antes de ejecutarse (DEC-19, EA04) o baja del servidor (RF72) */
    CANCELADA("Cancelada", EnumSet.of(EstadoOrden.PROGRAMADA, EstadoOrden.AUTORIZADA, EstadoOrden.EN_COLA)),
    /** Un aprobador rechazó el mantenimiento (RF47, RF48) */
    RECHAZADA("Rechazada", EnumSet.of(EstadoOrden.PENDIENTE_AUTORIZACION, EstadoOrden.AUTORIZADA)),
    /** Se cerraron todas las incidencias de la orden y ningún servidor sigue en cola o en ejecución (RF59) */
    CON_INCIDENCIA("Con incidencia", EnumSet.of(EstadoOrden.INCIDENCIA));

    private final String etiqueta;
    private final Set<EstadoOrden> desde;

    MotivoCierre(String etiqueta, Set<EstadoOrden> desde) {
        this.etiqueta = etiqueta;
        this.desde = desde;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** El motivo corresponde al estado desde el que se cierra la orden */
    public boolean admiteCierreDesde(EstadoOrden estado) {
        return desde.contains(estado);
    }
}
