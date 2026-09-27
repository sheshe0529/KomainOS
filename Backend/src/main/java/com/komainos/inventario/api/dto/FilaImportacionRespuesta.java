package com.komainos.inventario.api.dto;

import com.komainos.inventario.dominio.intercambio.ResultadoImportacion.Resultado;

/** Resultado de un registro importado; {@code detalle} explica un rechazo u omisión (HU08 CA3). */
public record FilaImportacionRespuesta(int fila, String hostname, Resultado resultado, Integer idServidor,
                                       String detalle) {
}
