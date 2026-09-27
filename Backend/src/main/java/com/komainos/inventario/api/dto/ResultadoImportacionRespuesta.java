package com.komainos.inventario.api.dto;

import java.util.List;

/** Resultado de una importación confirmada (RF12, HU08 CA3). */
public record ResultadoImportacionRespuesta(long creados, long actualizados, long omitidos, long rechazados,
                                            List<FilaImportacionRespuesta> filas) {
}
