package com.komainos.seguridad.service;

/**
 * Consulta del perfil del usuario: cuántos servidores vigentes tiene a su cargo
 * un responsable. La implementación vive en {@code inventario} (DEC-33).
 */
public interface PuertoServidoresACargo {

    /** Servidores no dados de baja de los que el usuario es responsable. */
    long contarACargoDe(Integer idUsuario);
}
