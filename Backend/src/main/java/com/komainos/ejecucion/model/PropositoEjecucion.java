package com.komainos.ejecucion.model;

/** Para qué se abre la conexión: decide cómo se interpreta la salida y qué responde el modo simulado */
public enum PropositoEjecucion {

    /** Conectividad, autenticación y permisos de la cuenta de servicio (RF71) y condición de inicio (RF52) */
    CONECTIVIDAD,
    /** Script de análisis en la revisión periódica (RF37) */
    ANALISIS_REVISION,
    /** Script de análisis en la evaluación previa (RF38) */
    ANALISIS_PREVIO,
    /** Script de análisis después de ejecutar la MOP (RF55) */
    ANALISIS_POSTERIOR,
    /** Prueba manual de una versión de análisis desde el catálogo, sin registrar una evaluación */
    PRUEBA_ANALISIS,
    /** Tarea de la MOP (RF52) */
    TAREA;

    public boolean esAnalisis() {
        return this == ANALISIS_REVISION || this == ANALISIS_PREVIO || this == ANALISIS_POSTERIOR
                || this == PRUEBA_ANALISIS;
    }
}
