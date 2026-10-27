package com.komainos.mantenimiento.model;

/** Etapa en la que se originó la incidencia (RF62) */
public enum OrigenIncidencia {
    /** Falló la MOP del servidor piloto o las condiciones de inicio de un servidor (RF52, RF58) */
    PRUEBA_PILOTO,
    FALLO_EJECUCION,
    /** Fin de la ventana permisiva o duración máxima por MOP (RF60) */
    TIEMPO_EXCEDIDO,
    VALIDACION_TECNICA_NO_CONFORME,
    RECHAZO_FUNCIONAL
}
