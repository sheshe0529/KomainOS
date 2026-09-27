package com.komainos.inventario.api.dto;

import java.util.List;

/** Vista previa de una importación antes de confirmarla (RF12, HU08 CA2). */
public record AnalisisImportacionRespuesta(String formato, int registros, long nuevos, long duplicados,
                                           long erroneos, long sobrescribibles,
                                           List<String> columnasReconocidas, List<String> columnasIgnoradas,
                                           List<FilaAnalisisRespuesta> filas) {
}
