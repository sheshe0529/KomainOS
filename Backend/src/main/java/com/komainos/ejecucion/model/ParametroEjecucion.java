package com.komainos.ejecucion.model;

import com.komainos.inventario.model.TipoDato;

/**
 * Valor con el que se ejecuta un parámetro declarado en la versión del script (RF25, RF52).
 *
 * @param valor texto ya validado contra el tipo; nulo si el parámetro es opcional y no tiene valor por defecto
 */
public record ParametroEjecucion(String nombre, TipoDato tipo, String valor) {
}
