package com.komainos.inventario.dto;

import com.komainos.inventario.model.Metrica;
import com.komainos.inventario.model.TipoDato;

public record MetricaRespuesta(Integer id, String codigo, String nombre, String unidad, TipoDato tipoDato,
                               boolean esLista, boolean activo) {

    public static MetricaRespuesta de(Metrica m) {
        return new MetricaRespuesta(m.getId(), m.getCodigo(), m.getNombre(), m.getUnidad(), m.getTipoDato(),
                m.isEsLista(), m.isActivo());
    }
}
