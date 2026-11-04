package com.komainos.mantenimiento.service;

import com.komainos.ejecucion.model.ListaEjecucion;
import com.komainos.inventario.model.ContextoEvaluacion;
import com.komainos.inventario.model.ListaReferencia;
import com.komainos.inventario.model.Metrica;
import com.komainos.inventario.model.Umbral;
import com.komainos.inventario.model.UmbralAplicado;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Las listas de referencia que el análisis recibe como entrada (R2.4, R3.1): por cada métrica de lista, la lista del
 * umbral que rige para el servidor en ese contexto, sea la general o la de una excepción. La misma lista con la que se
 * compara el resultado le dice al script qué revisar; por ejemplo, los archivos críticos y el permiso que exige la
 * política, sin repetirlos en un parámetro.
 */
public final class ListasAnalisis {

    private ListasAnalisis() {
    }

    /**
     * Una lista por cada métrica de lista que se mide en el servidor, aunque ningún umbral la use: así el script siempre
     * encuentra su variable, vacía si no hay nada que revisar. Si dos umbrales activos de la misma métrica usan listas
     * distintas, se unen sus elementos
     */
    public static List<ListaEjecucion> calcular(List<Metrica> metricas, List<Umbral> umbrales,
                                                ContextoEvaluacion contexto) {
        Map<String, Set<String>> porMetrica = new TreeMap<>();
        for (Metrica metrica : metricas) {
            // Una métrica que no se mide en este sistema operativo no llega al script (R3.1)
            if (metrica.isActivo() && metrica.isEsLista() && contexto.mide(metrica)) {
                porMetrica.put(metrica.getCodigo(), new LinkedHashSet<>());
            }
        }
        for (Umbral umbral : umbrales) {
            Set<String> elementos = porMetrica.get(umbral.getMetrica().getCodigo());
            ListaReferencia lista = elementos == null || !umbral.esDeLista() ? null
                    : UmbralAplicado.resolver(umbral, contexto).lista();
            if (lista != null) {
                elementos.addAll(lista.elementosVigentes());
            }
        }
        return porMetrica.entrySet().stream()
                .map(e -> new ListaEjecucion(e.getKey(), List.copyOf(e.getValue())))
                .toList();
    }
}
