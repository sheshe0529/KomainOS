package com.komainos.ejecucion.model;

import com.komainos.inventario.model.TipoDato;

import java.util.List;

/**
 * Valor con el que se ejecuta un parámetro declarado en la versión del script (RF25, RF52).
 *
 * @param valor escalar validado; nulo solo en versiones históricas que permitían parámetros vacíos
 * @param elementos nulo para un escalar; arreglo del catálogo para una lista, incluso si está vacía
 */
public record ParametroEjecucion(String nombre, TipoDato tipo, String valor, List<String> elementos) {
    public ParametroEjecucion {
        elementos = elementos == null ? null : List.copyOf(elementos);
    }

    public ParametroEjecucion(String nombre, TipoDato tipo, String valor) {
        this(nombre, tipo, valor, null);
    }

    public static ParametroEjecucion lista(String nombre, TipoDato tipoElemento, List<String> elementos) {
        return new ParametroEjecucion(nombre, tipoElemento, null, elementos);
    }
}
