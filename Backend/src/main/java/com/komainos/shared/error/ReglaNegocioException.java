package com.komainos.shared.error;

/**
 * La peticion esta bien formada pero viola una regla del proceso de
 * mantenimiento: una transicion de estado no permitida, una ventana permisiva
 * incompatible, una orden sin implementacion compatible.
 *
 * <p>Se traduce a 422 y no a 400 porque el cliente no puede corregirla
 * reformateando el cuerpo: el problema esta en el estado del sistema, no en la
 * sintaxis del mensaje.
 */
public class ReglaNegocioException extends RuntimeException {

    public ReglaNegocioException(String mensaje) {
        super(mensaje);
    }
}
