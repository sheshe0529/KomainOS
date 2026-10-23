package com.komainos.inventario.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Valores de un umbral que rigen para un servidor en un contexto, y de dónde salen (R2.4). Es lo que evaluacion_umbral
 * conserva como aplicado: operador y valores en uno numérico; lista y niveles en uno de lista.
 *
 * @param excepcion nula si se aplica el umbral general
 */
public record UmbralAplicado(Umbral umbral, UmbralExcepcion excepcion, OrigenUmbral origen,
                             OperadorComparacion operador, BigDecimal advertencia, BigDecimal critico,
                             ListaReferencia lista, NivelAlerta nivelFaltante, NivelAlerta nivelSobrante) {

    /**
     * Regla de resolución: entre las excepciones activas que alcanzan al servidor gana la de mayor prioridad (servidor,
     * grupo, entorno y criticidad); si no hay ninguna, el umbral general.
     */
    public static UmbralAplicado resolver(Umbral umbral, ContextoEvaluacion contexto) {
        return umbral.getExcepciones().stream()
                .filter(UmbralExcepcion::isActivo)
                .filter(e -> e.aplicaA(contexto))
                .min(Comparator.comparing(UmbralExcepcion::alcance))
                .map(e -> deExcepcion(umbral, e))
                .orElseGet(() -> global(umbral));
    }

    public static UmbralAplicado global(Umbral u) {
        return new UmbralAplicado(u, null, OrigenUmbral.GLOBAL, u.getOperador(), u.getValorAdvertencia(),
                u.getValorCritico(), u.getLista(), u.getNivelFaltante(), u.getNivelSobrante());
    }

    /** La excepción reemplaza los valores; operador y niveles siguen siendo los del umbral */
    private static UmbralAplicado deExcepcion(Umbral u, UmbralExcepcion e) {
        return u.esDeLista()
                ? new UmbralAplicado(u, e, e.alcance().comoOrigen(), null, null, null, e.getLista(),
                        u.getNivelFaltante(), u.getNivelSobrante())
                : new UmbralAplicado(u, e, e.alcance().comoOrigen(), u.getOperador(), e.getValorAdvertencia(),
                        e.getValorCritico(), null, null, null);
    }

    /** Se compara primero con el crítico: una medición que alcanza los dos niveles es crítica */
    public NivelAlerta evaluar(BigDecimal medicion) {
        if (critico != null && operador.seCumple(medicion, critico)) {
            return NivelAlerta.CRITICO;
        }
        if (advertencia != null && operador.seCumple(medicion, advertencia)) {
            return NivelAlerta.ADVERTENCIA;
        }
        return NivelAlerta.NORMAL;
    }

    /**
     * Faltan los esperados que no se informaron y sobran los informados que no se esperaban. Cada diferencia cuenta solo
     * si su nivel está definido y el resultado es el peor de los dos. Compara con los elementos vigentes de la lista;
     * para reconstruir una evaluación pasada se usa {@link ListaReferencia#elementosEn}.
     */
    public ResultadoLista evaluarLista(Collection<String> informados) {
        Set<String> reportados = new LinkedHashSet<>();
        for (String crudo : informados) {
            reportados.add(lista.canonico(crudo));
        }
        List<String> esperados = lista.elementosVigentes();
        List<String> faltantes = new ArrayList<>(esperados.stream().filter(e -> !reportados.contains(e)).toList());
        List<String> sobrantes = new ArrayList<>(reportados.stream().filter(r -> !esperados.contains(r)).toList());
        faltantes.sort(lista.getTipoDato().orden());
        sobrantes.sort(lista.getTipoDato().orden());

        NivelAlerta nivel = NivelAlerta.NORMAL;
        if (nivelFaltante != null && !faltantes.isEmpty()) {
            nivel = nivel.peor(nivelFaltante);
        }
        if (nivelSobrante != null && !sobrantes.isEmpty()) {
            nivel = nivel.peor(nivelSobrante);
        }
        return new ResultadoLista(nivel, faltantes, sobrantes);
    }

    public record ResultadoLista(NivelAlerta nivel, List<String> faltantes, List<String> sobrantes) {
    }
}
