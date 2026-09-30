package com.komainos.shared.util;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Instantes con la misma precision que las columnas TIMESTAMP(0).
 *
 * <p>La base redondea a segundos. Truncar antes de persistir evita que un
 * valor leido de vuelta difiera del que se guardo (por ejemplo, al comparar
 * una fecha programada recien calculada con la almacenada).
 */
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
