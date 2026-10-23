package com.komainos.inventario.model;

import java.math.BigDecimal;

/** Cómo se compara la medición con los valores de un umbral numérico (enum_operador_comparacion, RF16) */
public enum OperadorComparacion {

    MAYOR(">"),
    MAYOR_IGUAL("≥"),
    MENOR("<"),
    MENOR_IGUAL("≤"),
    IGUAL("="),
    DISTINTO("≠");

    private final String simbolo;

    OperadorComparacion(String simbolo) {
        this.simbolo = simbolo;
    }

    public String simbolo() {
        return simbolo;
    }

    /** Si la medición alcanza el límite: con MAYOR, 85 alcanza 80 */
    public boolean seCumple(BigDecimal medicion, BigDecimal limite) {
        int comparacion = medicion.compareTo(limite);
        return switch (this) {
            case MAYOR -> comparacion > 0;
            case MAYOR_IGUAL -> comparacion >= 0;
            case MENOR -> comparacion < 0;
            case MENOR_IGUAL -> comparacion <= 0;
            case IGUAL -> comparacion == 0;
            case DISTINTO -> comparacion != 0;
        };
    }

    /**
     * Misma regla que ck_umbral_coherencia (RF16, HU12 CA2): con mayor el crítico no es menor que la advertencia, con
     * menor no es mayor; con igual o distinto no hay orden. Si falta uno de los dos valores no hay nada que comparar.
     */
    public boolean esCoherente(BigDecimal advertencia, BigDecimal critico) {
        if (advertencia == null || critico == null) {
            return true;
        }
        return switch (this) {
            case MAYOR, MAYOR_IGUAL -> critico.compareTo(advertencia) >= 0;
            case MENOR, MENOR_IGUAL -> critico.compareTo(advertencia) <= 0;
            case IGUAL, DISTINTO -> true;
        };
    }
}
