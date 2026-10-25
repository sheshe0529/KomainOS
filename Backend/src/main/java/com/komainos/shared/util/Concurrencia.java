package com.komainos.shared.util;

import com.komainos.shared.exception.ConflictoException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Control optimista de concurrencia sin columna de versión: el modelo de datos ya registra fecha_actualizacion en cada
 * tabla editable, y el formulario devuelve la que leyó. Si no coincide, otro usuario guardó antes y se responde 409 en
 * lugar de sobrescribir su cambio. Sin versión esperada (importación, procesos) no se comprueba.
 */
public final class Concurrencia {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
            .withZone(ZoneId.of("America/Lima"));

    private Concurrencia() {
    }

    /** sujeto en minúscula y con artículo («el servidor X»). fecha_actualizacion es TIMESTAMP(0): se compara en segundos */
    public static void exigirVersion(Instant esperada, Instant actual, String sujeto) {
        if (esperada == null || actual == null) {
            return;
        }
        if (!esperada.truncatedTo(ChronoUnit.SECONDS).equals(actual.truncatedTo(ChronoUnit.SECONDS))) {
            throw new ConflictoException(("Hubo otro cambio en %s después de que abrió el formulario (última "
                    + "modificación: %s). Recargue para verlo antes de guardar").formatted(sujeto, FORMATO.format(actual)));
        }
    }
}
