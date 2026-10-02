package com.komainos.inventario.dto;

import java.util.List;

public record ResultadoImportacionRespuesta(long creados, long actualizados, long omitidos, long rechazados,
                                            List<FilaImportacionRespuesta> filas) {
}
