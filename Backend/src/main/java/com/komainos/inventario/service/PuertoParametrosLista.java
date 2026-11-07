package com.komainos.inventario.service;

import java.util.Map;

/** Uso de listas en versiones de scripts, consultado sin hacer depender inventario del catálogo (RF25/RF26). */
public interface PuertoParametrosLista {
    Map<Integer, Long> contarPorLista();
    long contarDeLista(Integer idLista);
}
