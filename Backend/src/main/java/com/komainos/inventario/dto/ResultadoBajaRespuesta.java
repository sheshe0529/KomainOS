package com.komainos.inventario.dto;

public record ResultadoBajaRespuesta(boolean aplicada, int ordenesRetiradas, String mensaje,
                                     SolicitudBajaRespuesta solicitud, FichaServidorRespuesta servidor) {
}
