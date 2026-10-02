package com.komainos.seguridad.service;

/** Implementado en inventario para no formar un ciclo (DEC-33) */
public interface PuertoServidoresACargo {

    long contarACargoDe(Integer idUsuario);
}
