package com.komainos.mantenimiento.model;

/** Etapa del ciclo a la que pertenece un evento de la actividad (DEC-59) */
public enum FaseActividad {
    /** Cambios de estado de la orden */
    ORDEN,
    /** Evaluación previa y revisión periódica */
    EVALUACION,
    /** Comprobación de acceso y tareas de la MOP */
    EJECUCION,
    /** Análisis posterior y validación */
    VALIDACION
}
