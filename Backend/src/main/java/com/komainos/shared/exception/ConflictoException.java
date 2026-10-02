package com.komainos.shared.exception;

/** Choca con un registro existente: se traduce a 409 */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
