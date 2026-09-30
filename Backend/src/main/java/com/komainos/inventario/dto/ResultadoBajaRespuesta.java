package com.komainos.inventario.dto;

/**
 * Resultado de solicitar una baja (RF72): se aplico de inmediato o quedo
 * pendiente porque hay un mantenimiento en curso.
 */
public record ResultadoBajaRespuesta(boolean aplicada, int ordenesRetiradas, String mensaje,
                                     SolicitudBajaRespuesta solicitud, FichaServidorRespuesta servidor) {
}
