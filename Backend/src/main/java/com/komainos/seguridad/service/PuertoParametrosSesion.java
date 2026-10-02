package com.komainos.seguridad.service;

/** Implementado en inventario: la dependencia va del inventario hacia seguridad y no forma un ciclo (DEC-33) */
public interface PuertoParametrosSesion {

    int minutosExpiracionToken();
}
