package com.komainos.shared.error;

/**
 * El recurso pedido no existe o esta fuera del alcance del usuario.
 *
 * <p>Deliberadamente no se distingue "no existe" de "no te corresponde": el
 * responsable solo ve sus servidores, y devolver 403 en lugar de 404 le
 * confirmaria la existencia de un servidor ajeno.
 */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }

    public static RecursoNoEncontradoException de(String entidad, Object id) {
        return new RecursoNoEncontradoException("No se encontró %s con identificador %s".formatted(entidad, id));
    }
}
