package com.komainos.shared.util.archivo;

import java.util.List;
import java.util.Map;

/**
 * Registro leido de un archivo.
 *
 * @param numero    fila de la hoja (XLSX, CSV; el encabezado es la fila 1) o
 *                  posicion del registro (JSON, YAML; el primero es el 1): lo
 *                  que el usuario ve al abrir el archivo
 * @param valores   texto de cada celda por clave normalizada; sin valor = ausente
 * @param problemas defectos de estructura del registro (por ejemplo, un valor
 *                  que no es simple en JSON)
 */
public record FilaArchivo(int numero, Map<String, String> valores, List<String> problemas) {

    public String valor(String clave) {
        return valores.get(clave);
    }
}
