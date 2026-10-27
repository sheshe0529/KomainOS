package com.komainos.mantenimiento.model;

/** Ejecución de un script de análisis: termina con métricas o con el error que lo impidió */
public enum EstadoEvaluacion {
    EN_CURSO,
    FINALIZADA,
    FALLIDA
}
