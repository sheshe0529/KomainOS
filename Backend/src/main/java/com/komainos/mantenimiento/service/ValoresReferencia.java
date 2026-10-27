package com.komainos.mantenimiento.service;

import com.komainos.ejecucion.model.MetricaReferencia;
import com.komainos.ejecucion.model.MetricaReferencia.Valor;
import com.komainos.inventario.model.ContextoEvaluacion;
import com.komainos.inventario.model.Metrica;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.OperadorComparacion;
import com.komainos.inventario.model.TipoDato;
import com.komainos.inventario.model.Umbral;
import com.komainos.inventario.model.UmbralAplicado;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Valores de cada métrica activa que producen los niveles normal, advertencia y crítico con los umbrales que rigen para
 * un servidor (DEC-41). Los usa el modo simulado para responder con cualquier catálogo de métricas: cada candidato se
 * comprueba con la misma evaluación del ciclo, así el nivel simulado es exactamente el que se pide.
 */
public final class ValoresReferencia {

    private static final String ELEMENTO_INESPERADO = "elemento_no_esperado";

    private ValoresReferencia() {
    }

    public static List<MetricaReferencia> calcular(List<Metrica> metricas, List<Umbral> umbrales,
                                                   ContextoEvaluacion contexto) {
        List<MetricaReferencia> referencias = new ArrayList<>();
        for (Metrica metrica : metricas) {
            if (!metrica.isActivo()) {
                continue;
            }
            List<UmbralAplicado> aplicados = umbrales.stream()
                    .filter(u -> u.getMetrica().getCodigo().equals(metrica.getCodigo()))
                    .map(u -> UmbralAplicado.resolver(u, contexto))
                    .toList();
            referencias.add(metrica.isEsLista() ? deLista(metrica, aplicados) : escalar(metrica, aplicados));
        }
        return referencias;
    }

    private static MetricaReferencia escalar(Metrica metrica, List<UmbralAplicado> aplicados) {
        TipoDato tipo = metrica.getTipoDato();
        List<BigDecimal> candidatos = candidatos(tipo, aplicados);
        return new MetricaReferencia(metrica.getCodigo(),
                elegir(candidatos, aplicados, NivelAlerta.NORMAL).map(Valor::escalar).orElse(Valor.escalar(BigDecimal.ZERO)),
                elegir(candidatos, aplicados, NivelAlerta.ADVERTENCIA).map(Valor::escalar).orElse(null),
                elegir(candidatos, aplicados, NivelAlerta.CRITICO).map(Valor::escalar).orElse(null));
    }

    /** Normal: exactamente la lista esperada. Los otros niveles: falta un elemento o sobra uno, según los niveles */
    private static MetricaReferencia deLista(Metrica metrica, List<UmbralAplicado> aplicados) {
        List<String> esperados = aplicados.stream()
                .filter(a -> a.lista() != null)
                .findFirst()
                .map(a -> a.lista().elementosVigentes())
                .orElse(List.of());
        List<List<String>> variantes = new ArrayList<>();
        variantes.add(esperados);
        if (!esperados.isEmpty()) {
            variantes.add(esperados.subList(1, esperados.size()));
        }
        List<String> conSobrante = new ArrayList<>(esperados);
        conSobrante.add(metrica.getTipoDato().esNumerico() ? "999999" : ELEMENTO_INESPERADO);
        variantes.add(conSobrante);
        return new MetricaReferencia(metrica.getCodigo(),
                elegirLista(variantes, aplicados, NivelAlerta.NORMAL).map(Valor::lista).orElse(Valor.lista(esperados)),
                elegirLista(variantes, aplicados, NivelAlerta.ADVERTENCIA).map(Valor::lista).orElse(null),
                elegirLista(variantes, aplicados, NivelAlerta.CRITICO).map(Valor::lista).orElse(null));
    }

    /** Los límites, valores a un paso de ellos y puntos medios: entre ellos está cada nivel alcanzable */
    private static List<BigDecimal> candidatos(TipoDato tipo, List<UmbralAplicado> aplicados) {
        if (tipo == TipoDato.BOOLEAN) {
            return List.of(BigDecimal.ZERO, BigDecimal.ONE);
        }
        Set<BigDecimal> valores = new LinkedHashSet<>();
        for (UmbralAplicado a : aplicados) {
            normalSugerido(a).ifPresent(valores::add);
        }
        for (UmbralAplicado a : aplicados) {
            for (BigDecimal limite : new BigDecimal[]{a.advertencia(), a.critico()}) {
                if (limite == null) {
                    continue;
                }
                BigDecimal paso = paso(limite);
                valores.add(limite);
                valores.add(limite.add(paso));
                valores.add(limite.subtract(paso));
            }
            if (a.advertencia() != null && a.critico() != null) {
                valores.add(a.advertencia().add(a.critico()).divide(BigDecimal.TWO, 6, RoundingMode.HALF_UP));
            }
        }
        valores.add(BigDecimal.ZERO);
        return valores.stream().map(v -> redondear(tipo, v)).distinct().toList();
    }

    /** Lejos del primer límite que se alcanza: el valor típico de un servidor sano */
    private static Optional<BigDecimal> normalSugerido(UmbralAplicado a) {
        OperadorComparacion op = a.operador();
        if (op == null) {
            return Optional.empty();
        }
        List<BigDecimal> limites = new ArrayList<>();
        if (a.advertencia() != null) {
            limites.add(a.advertencia());
        }
        if (a.critico() != null) {
            limites.add(a.critico());
        }
        if (limites.isEmpty()) {
            return Optional.empty();
        }
        return switch (op) {
            case MAYOR, MAYOR_IGUAL -> {
                BigDecimal menor = limites.stream().min(BigDecimal::compareTo).orElseThrow();
                BigDecimal sugerido = menor.subtract(menor.abs().multiply(new BigDecimal("0.2")).max(BigDecimal.ONE));
                // Un porcentaje o un conteo positivos no se simulan con valores negativos
                yield Optional.of(menor.signum() > 0 ? sugerido.max(BigDecimal.ZERO) : sugerido);
            }
            case MENOR, MENOR_IGUAL -> {
                BigDecimal mayor = limites.stream().max(BigDecimal::compareTo).orElseThrow();
                yield Optional.of(mayor.add(mayor.abs().multiply(new BigDecimal("0.2")).max(BigDecimal.ONE)));
            }
            case IGUAL -> Optional.of(limites.getFirst().add(BigDecimal.ONE));
            case DISTINTO -> Optional.of(limites.getFirst());
        };
    }

    private static Optional<BigDecimal> elegir(List<BigDecimal> candidatos, List<UmbralAplicado> aplicados,
                                               NivelAlerta objetivo) {
        return candidatos.stream().filter(v -> nivel(v, aplicados) == objetivo).findFirst();
    }

    private static Optional<List<String>> elegirLista(List<List<String>> variantes, List<UmbralAplicado> aplicados,
                                                      NivelAlerta objetivo) {
        return variantes.stream().filter(v -> nivelLista(v, aplicados) == objetivo).findFirst();
    }

    private static NivelAlerta nivel(BigDecimal valor, List<UmbralAplicado> aplicados) {
        return aplicados.stream()
                .filter(a -> a.operador() != null)
                .map(a -> a.evaluar(valor))
                .reduce(NivelAlerta.NORMAL, NivelAlerta::peor);
    }

    private static NivelAlerta nivelLista(List<String> elementos, List<UmbralAplicado> aplicados) {
        return aplicados.stream()
                .filter(a -> a.lista() != null)
                .map(a -> a.evaluarLista(elementos).nivel())
                .reduce(NivelAlerta.NORMAL, NivelAlerta::peor);
    }

    private static BigDecimal paso(BigDecimal limite) {
        BigDecimal relativo = limite.abs().multiply(new BigDecimal("0.05"));
        return relativo.compareTo(BigDecimal.ONE) < 0 ? BigDecimal.ONE : relativo;
    }

    private static BigDecimal redondear(TipoDato tipo, BigDecimal valor) {
        Objects.requireNonNull(valor);
        if (tipo == TipoDato.INTEGER) {
            return valor.setScale(0, RoundingMode.HALF_UP);
        }
        // Sin notación científica: 80.00 se informa como 80 y no como 8E+1
        BigDecimal reducido = valor.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
        return reducido.scale() < 0 ? reducido.setScale(0) : reducido;
    }
}
