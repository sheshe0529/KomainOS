package com.komainos.ejecucion.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Una entrada de la lista «metricas» que devuelve el script de análisis.
 *
 * @param instancia vacía si la métrica no distingue instancias (por ejemplo, un disco o una interfaz)
 * @param valor     en las métricas escalares; nulo en las de lista
 * @param elementos en las métricas de lista; nulo en las escalares
 */
public record MedicionInformada(String codigo, String instancia, BigDecimal valor, List<String> elementos) {
}
