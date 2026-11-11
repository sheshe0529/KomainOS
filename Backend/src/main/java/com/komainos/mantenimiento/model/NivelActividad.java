package com.komainos.mantenimiento.model;

/** Gravedad de un evento de la actividad, como en un registro (DEC-59) */
public enum NivelActividad {
    INFO,
    /** Terminó, pero algo conviene revisarlo: por ejemplo, un acceso sin privilegios de administrador */
    WARNING,
    ERROR
}
