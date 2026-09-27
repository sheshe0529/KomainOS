package com.komainos.inventario.dominio;

/** Estado de una solicitud de baja ({@code enum_estado_solicitud_baja}; RF72). */
public enum EstadoSolicitudBaja {

    /** Espera a que finalice el mantenimiento en curso. */
    PENDIENTE,

    /** La baja se aplico. */
    APLICADA
}
