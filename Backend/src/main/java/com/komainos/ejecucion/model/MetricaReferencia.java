package com.komainos.ejecucion.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Valores de una métrica que corresponden a cada nivel según los umbrales que rigen para el servidor. El adaptador real
 * los ignora: el modo simulado responde con ellos, así el ciclo se prueba con cualquier catálogo de métricas.
 *
 * @param advertencia nulo si ningún umbral define ese nivel
 * @param critico     nulo si ningún umbral define ese nivel
 */
public record MetricaReferencia(String codigo, Valor normal, Valor advertencia, Valor critico) {

    /** @param elementos solo en métricas de lista; en las escalares, el valor numérico */
    public record Valor(BigDecimal valor, List<String> elementos) {

        public static Valor escalar(BigDecimal valor) {
            return new Valor(valor, null);
        }

        public static Valor lista(List<String> elementos) {
            return new Valor(null, List.copyOf(elementos));
        }
    }
}
