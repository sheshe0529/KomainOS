package com.komainos.shared.exception;

/** 429: la operación se rechaza sin evaluarla hasta que pase la espera */
public class DemasiadosIntentosException extends RuntimeException {

    private final long segundosEspera;

    public DemasiadosIntentosException(long segundosEspera) {
        super("Demasiados intentos fallidos. Espere %s antes de volver a intentarlo".formatted(espera(segundosEspera)));
        this.segundosEspera = segundosEspera;
    }

    public long getSegundosEspera() {
        return segundosEspera;
    }

    private static String espera(long segundos) {
        long minutos = (segundos + 59) / 60;
        return minutos <= 1 ? "un minuto" : "%d minutos".formatted(minutos);
    }
}
