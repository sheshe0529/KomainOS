package com.komainos.shared.exception;

/**
 * La operacion choca con un registro existente: un hostname duplicado, un
 * grupo con el mismo nombre, un servidor que ya tiene una ejecucion en curso.
 *
 * <p>Se traduce a 409 para que el frontend pueda ofrecer "ya existe, ver el
 * registro" en vez de mostrar un error de validacion generico.
 */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
