package com.komainos.mantenimiento.model;

import java.time.Instant;

/**
 * Programación vigente de una orden (R2.4): la fecha objetivo calculada con periodicidad y factor (RF64), el intervalo
 * asignado por el algoritmo o el administrador, el inicio de la evaluación previa (RF38) y la ventana permisiva aplicada.
 */
public record Programacion(Instant fechaObjetivo, Instant inicio, Instant fin, Instant evaluacion,
                           Instant inicioVentana, Instant finVentana) {
}
