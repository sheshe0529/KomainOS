package com.komainos.mantenimiento.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Transiciones de una tarea de la MOP (R2.1, tabla 8): una tarea pendiente se ejecuta o queda no iniciada */
public enum EstadoTareaMop {
    PENDIENTE,
    EN_EJECUCION,
    FINALIZADA,
    FALLIDA,
    NO_INICIADA;

    private static final Map<EstadoTareaMop, Set<EstadoTareaMop>> TRANSICIONES = Map.of(
            PENDIENTE, EnumSet.of(EN_EJECUCION, NO_INICIADA),
            EN_EJECUCION, EnumSet.of(FINALIZADA, FALLIDA),
            FINALIZADA, EnumSet.noneOf(EstadoTareaMop.class),
            FALLIDA, EnumSet.noneOf(EstadoTareaMop.class),
            NO_INICIADA, EnumSet.noneOf(EstadoTareaMop.class));

    public boolean puedePasarA(EstadoTareaMop destino) {
        return TRANSICIONES.get(this).contains(destino);
    }
}
