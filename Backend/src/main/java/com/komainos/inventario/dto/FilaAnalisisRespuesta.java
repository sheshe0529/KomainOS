package com.komainos.inventario.dto;

import com.komainos.inventario.service.intercambio.AnalisisImportacion.EstadoFila;
import com.komainos.shared.dto.ReferenciaSimple;

import java.util.List;

public record FilaAnalisisRespuesta(int fila, EstadoFila estado, String hostname, String direccionIp,
                                    List<String> motivos, ReferenciaSimple servidorExistente,
                                    boolean sobrescribible, List<String> camposModificados) {
}
