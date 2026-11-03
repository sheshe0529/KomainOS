package com.komainos.inventario.dto;

import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.Metrica;
import com.komainos.inventario.model.TipoDato;

/** @param soAplica familia en la que la informa el análisis; nula si se mide en Linux y en Windows (R3.1) */
public record MetricaRespuesta(Integer id, String codigo, String nombre, String descripcion, String unidad,
                               TipoDato tipoDato, boolean esLista, FamiliaSistemaOperativo soAplica, boolean activo) {

    public static MetricaRespuesta de(Metrica m) {
        return new MetricaRespuesta(m.getId(), m.getCodigo(), m.getNombre(), m.getDescripcion(), m.getUnidad(),
                m.getTipoDato(), m.isEsLista(), m.getSoAplica(), m.isActivo());
    }
}
