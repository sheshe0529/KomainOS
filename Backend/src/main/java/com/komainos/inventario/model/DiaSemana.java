package com.komainos.inventario.model;

/** En el orden de la semana ISO: diasHasta usa el ordinal */
public enum DiaSemana {

    LUNES,
    MARTES,
    MIERCOLES,
    JUEVES,
    VIERNES,
    SABADO,
    DOMINGO;

    /** Días que hay que avanzar desde este día hasta el otro (0 a 6) */
    public int diasHasta(DiaSemana otro) {
        return Math.floorMod(otro.ordinal() - ordinal(), 7);
    }
}
