package com.komainos.shared.util.archivo;

import java.util.List;
import java.util.Map;

/** numero: fila de la hoja (el encabezado es la 1) o posición del registro en JSON y YAML */
public record FilaArchivo(int numero, Map<String, String> valores, Map<String, String> literales, List<String> problemas) {

    public FilaArchivo(int numero, Map<String, String> valores, List<String> problemas) {
        this(numero, valores, valores, problemas);
    }

    public String valor(String clave) {
        return valores.get(clave);
    }

    /** Sin recortar espacios: para contraseñas, donde un espacio puede ser parte del secreto */
    public String literal(String clave) {
        return literales.get(clave);
    }
}
