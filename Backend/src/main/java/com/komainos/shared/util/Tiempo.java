package com.komainos.shared.util;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Trunca a segundos como TIMESTAMP(0): un valor leído de vuelta no difiere del guardado */
public final class Tiempo {

    private Tiempo() {
    }

    public static Instant ahora(Clock reloj) {
        return reloj.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    public static Instant ahora() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }
}
