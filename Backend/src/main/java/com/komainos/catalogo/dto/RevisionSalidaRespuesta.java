package com.komainos.catalogo.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Resultado de comprobar una salida de análisis contra el contrato y el catálogo de métricas.
 *
 * @param faltantes códigos que exigen los umbrales activos y no aparecen en la salida
 */
public record RevisionSalidaRespuesta(boolean valida, List<String> errores, List<MetricaRevisada> metricas,
                                      List<String> faltantes) {

    /** @param reconocida existe como métrica activa del catálogo; si no, el ciclo la ignora */
    public record MetricaRevisada(String codigo, String instancia, BigDecimal valor, List<String> elementos,
                                  boolean reconocida, String nombre, String unidad) {
    }
}
