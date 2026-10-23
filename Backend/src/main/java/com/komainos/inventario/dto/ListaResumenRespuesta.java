package com.komainos.inventario.dto;

import com.komainos.inventario.model.ListaReferencia;
import com.komainos.inventario.model.TipoDato;

import java.util.List;

/** Lista con sus elementos vigentes, tal como la usa un umbral o una excepción */
public record ListaResumenRespuesta(Integer id, String nombre, TipoDato tipoDato, boolean activo, List<String> elementos) {

    public static ListaResumenRespuesta de(ListaReferencia l) {
        return l == null ? null : new ListaResumenRespuesta(l.getId(), l.getNombre(), l.getTipoDato(), l.isActivo(),
                l.elementosVigentes());
    }
}
