package com.komainos.inventario.dto;

import com.komainos.inventario.service.intercambio.AnalisisImportacion.EstadoFila;
import com.komainos.shared.dto.ReferenciaSimple;

import java.util.List;

/**
 * Registro del archivo clasificado en la vista previa (HU08 CA2).
 *
 * @param fila              fila de la hoja (XLSX, CSV) o posición del registro (JSON, YAML)
 * @param servidorExistente servidor registrado con el que coincide
 * @param sobrescribible    si puede confirmarse que reemplace al existente (HU08 CA4)
 * @param camposModificados datos del existente que cambiarían al sobrescribir
 */
public record FilaAnalisisRespuesta(int fila, EstadoFila estado, String hostname, String direccionIp,
                                    List<String> motivos, ReferenciaSimple servidorExistente,
                                    boolean sobrescribible, List<String> camposModificados) {
}
