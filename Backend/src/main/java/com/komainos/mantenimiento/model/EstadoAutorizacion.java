package com.komainos.mantenimiento.model;

/** Decisión de cada aprobador dentro de una ronda (RF44, RF47, RF48, RF50) */
public enum EstadoAutorizacion {
    PENDIENTE,
    APROBADA,
    RECHAZADA,
    FECHA_PROPUESTA,
    VENCIDA
}
