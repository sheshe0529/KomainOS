package com.komainos.seguridad.service;

/**
 * Duración de la sesión que seguridad necesita para emitir el token (RF02),
 * sin depender del componente que administra los parámetros del sistema.
 *
 * <p>La implementación vive en {@code inventario}; así las dependencias van del
 * inventario hacia seguridad y no forman un ciclo (DEC-33).
 */
public interface PuertoParametrosSesion {

    /** Minutos de validez del token según {@code configuracion_sistema}. */
    int minutosExpiracionToken();
}
