package com.komainos.inventario.repository;

/** Cuántos registros referencian a cada id, por ejemplo los umbrales que usan una lista */
public record ConteoPorId(Integer id, long cantidad) {
}
