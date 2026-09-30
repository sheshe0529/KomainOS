package com.komainos.shared.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Intervalo semiabierto [inicio, fin) sobre la linea de tiempo.
 *
 * <p>Semiabierto a proposito: dos reservas consecutivas (una termina a las
 * 03:00 y la otra empieza a las 03:00) no se solapan, y dos ventanas contiguas
 * se pueden fusionar sin contar dos veces el instante de union.
 */
public record Intervalo(Instant inicio, Instant fin) {

    public Intervalo {
        if (inicio == null || fin == null || !fin.isAfter(inicio)) {
            throw new IllegalArgumentException("Intervalo invalido: [" + inicio + ", " + fin + ")");
        }
    }

    public Duration duracion() {
        return Duration.between(inicio, fin);
    }

    public boolean seSolapaCon(Intervalo otro) {
        return inicio.isBefore(otro.fin) && otro.inicio.isBefore(fin);
    }

    public boolean contiene(Instant instante) {
        return !instante.isBefore(inicio) && instante.isBefore(fin);
    }

    /** Verdadero si el otro intervalo queda completamente dentro de este. */
    public boolean contiene(Intervalo otro) {
        return !otro.inicio.isBefore(inicio) && !otro.fin.isAfter(fin);
    }
}
