package com.komainos.shared.util.archivo;

import java.util.List;

/**
 * Contenido de un archivo de intercambio.
 *
 * @param columnas claves normalizadas en el orden en que aparecen
 * @param filas    registros con datos (las filas vacias se omiten)
 */
public record TablaArchivo(List<String> columnas, List<FilaArchivo> filas) {
}
