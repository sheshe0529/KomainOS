package com.komainos.shared.exception;

/** Viola una regla del proceso: 422 y no 400 porque el problema está en el estado del sistema, no en la sintaxis */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
