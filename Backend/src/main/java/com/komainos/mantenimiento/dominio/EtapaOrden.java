package com.komainos.mantenimiento.dominio;

/**
 * Etapa del ciclo de mantenimiento (RF34). Es un atributo derivado del estado
 * y no se persiste (comentario de la seccion 6 del DDL).
 */
public enum EtapaOrden {
    PLANIFICACION,
    EVALUACION,
    AUTORIZACION,
    EJECUCION,
    VALIDACION,
    INCIDENCIA,
    CIERRE
}
