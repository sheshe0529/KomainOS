package com.komainos.ejecucion.model;

import java.util.List;
import java.util.Locale;

/**
 * Elementos de la lista de referencia que rige para el servidor en una métrica de lista (R2.4). El análisis los recibe
 * en la variable {@code KOMAINOS_LISTA_<CÓDIGO>}: así revisa exactamente lo que el umbral compara, por ejemplo los
 * archivos críticos y su permiso, sin repetirlo en un parámetro del script.
 */
public record ListaEjecucion(String codigoMetrica, List<String> elementos) {

    public static final String PREFIJO = "KOMAINOS_LISTA_";

    public ListaEjecucion {
        elementos = List.copyOf(elementos);
    }

    /** El código de la métrica en mayúsculas; cualquier carácter que no sirva en un nombre de variable pasa a guion bajo */
    public String variable() {
        return PREFIJO + codigoMetrica.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "_");
    }
}
