package com.komainos.mantenimiento.model;

/** Cómo terminó el paso que informa un evento; nulo en los eventos que solo marcan un inicio (DEC-59) */
public enum EstadoActividad {
    EN_CURSO,
    COMPLETADO,
    FALLIDO
}
