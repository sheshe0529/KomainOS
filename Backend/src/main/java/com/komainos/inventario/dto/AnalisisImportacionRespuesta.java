package com.komainos.inventario.dto;

import java.util.List;

public record AnalisisImportacionRespuesta(String formato, int registros, long nuevos, long duplicados,
                                           long erroneos, long sobrescribibles,
                                           List<String> columnasReconocidas, List<String> columnasIgnoradas,
                                           List<FilaAnalisisRespuesta> filas) {
}
