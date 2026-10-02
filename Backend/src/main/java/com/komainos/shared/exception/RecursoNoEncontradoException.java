package com.komainos.shared.exception;

/** No distingue "no existe" de "no te corresponde": un 403 confirmaría que existe un servidor ajeno */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public static RecursoNoEncontradoException de(String entidad, Object id) {
        return new RecursoNoEncontradoException("No se encontró %s con identificador %s".formatted(entidad, id));
    }
}
