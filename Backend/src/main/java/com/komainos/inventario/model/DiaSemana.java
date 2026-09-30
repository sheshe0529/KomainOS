package com.komainos.inventario.model;

import java.time.DayOfWeek;

/** Dia de la semana de una ventana permisiva ({@code enum_dia_semana}). */
public enum DiaSemana {

    LUNES(DayOfWeek.MONDAY),
    MARTES(DayOfWeek.TUESDAY),
    MIERCOLES(DayOfWeek.WEDNESDAY),
    JUEVES(DayOfWeek.THURSDAY),
    VIERNES(DayOfWeek.FRIDAY),
    SABADO(DayOfWeek.SATURDAY),
    DOMINGO(DayOfWeek.SUNDAY);

    private final DayOfWeek diaIso;

    DiaSemana(DayOfWeek diaIso) {
        this.diaIso = diaIso;
    }

    public DayOfWeek aDayOfWeek() {
        return diaIso;
    }

    /** Dias que hay que avanzar desde este dia para llegar al otro (0 a 6). */
    public int diasHasta(DiaSemana otro) {
        return Math.floorMod(otro.ordinal() - ordinal(), 7);
    }
}
