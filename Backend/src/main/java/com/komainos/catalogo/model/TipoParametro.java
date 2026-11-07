package com.komainos.catalogo.model;

import com.komainos.inventario.model.TipoDato;

/** Forma de una entrada de script (RF25); LISTA referencia un catálogo, no cambia el tipo de sus elementos. */
public enum TipoParametro {
    STRING, INTEGER, DECIMAL, BOOLEAN, LISTA;

    public TipoDato tipoEscalar() {
        if (this == LISTA) {
            throw new IllegalStateException("Una lista toma el tipo de sus elementos del catálogo");
        }
        return TipoDato.valueOf(name());
    }
}
