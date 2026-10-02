package com.komainos.inventario.dto;

import com.komainos.inventario.service.intercambio.ResultadoImportacion.Resultado;

public record FilaImportacionRespuesta(int fila, String hostname, Resultado resultado, Integer idServidor,
                                       String detalle) {
}
