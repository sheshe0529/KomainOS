package com.komainos.ejecucion.config;

/**
 * Comportamiento que el modo SIMULADO reproduce para un servidor, para probar cada rama del ciclo sin conectarse a él.
 * Los valores de las métricas salen de los umbrales vigentes, así funcionan con cualquier catálogo de métricas.
 */
public enum EscenarioSimulado {

    /** Revisión normal, evaluación previa en advertencia (genera MOP) y evaluación posterior normal */
    ESTANDAR,
    /** Todo normal: la evaluación previa no activa tareas y la orden se cierra sin intervención */
    SALUDABLE,
    /** La revisión periódica encuentra advertencias: solo alerta, sin generar una orden (RF37, CP102) */
    ADVERTENCIA,
    /** La revisión y la evaluación previa alcanzan el nivel crítico (RF40) */
    CRITICO,
    /** La evaluación posterior sigue fuera de umbral: validación técnica no conforme (RF55) */
    NO_CONFORME,
    /** Las tareas de mantenimiento terminan con código de error (RF59) */
    FALLO_TAREA,
    /** El servidor no responde: error de comunicación (RF71) */
    SIN_CONEXION,
    /** El servidor rechaza la cuenta de servicio: error de autenticación (RF71) */
    SIN_AUTENTICACION,
    /** La cuenta entra pero no es administradora: permisos insuficientes (RF71) */
    SIN_PERMISOS
}
