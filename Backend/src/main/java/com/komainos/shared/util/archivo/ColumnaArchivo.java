package com.komainos.shared.util.archivo;

/**
 * Columna de un archivo de intercambio.
 *
 * @param clave    nombre en JSON y YAML; es la etiqueta normalizada (ver {@link ClaveColumna})
 * @param etiqueta encabezado legible de XLSX y CSV
 */
public record ColumnaArchivo(String clave, String etiqueta) {
}
