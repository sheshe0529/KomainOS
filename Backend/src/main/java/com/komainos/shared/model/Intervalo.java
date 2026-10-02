package com.komainos.shared.model;

import java.time.Duration;
import java.time.Instant;

/** Semiabierto [inicio, fin): dos reservas consecutivas no se solapan */
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

    public boolean contiene(Intervalo otro) {
        return !otro.inicio.isBefore(inicio) && !otro.fin.isAfter(fin);
    }
}
