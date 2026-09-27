package com.komainos.inventario.api.dto;

/**
 * Columna del inventario disponible para exportar (HU09 CA2) y su papel en la
 * importación (HU08).
 *
 * @param clave       nombre en JSON y YAML, y valor del parámetro {@code columnas}
 * @param etiqueta    encabezado en XLSX y CSV
 * @param importable  si se lee al importar (las demás las administra el sistema)
 * @param obligatoria si es obligatoria al importar
 */
public record ColumnaInventarioRespuesta(String clave, String etiqueta, boolean importable, boolean obligatoria) {
}
