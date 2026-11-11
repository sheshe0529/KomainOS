package com.komainos.catalogo.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Ayuda a corregir un script de análisis que informa una métrica con un código distinto del catálogo: KomainOS compara
 * los códigos de forma exacta, así que «uptime» no cuenta como «uptime_dias» (DEC-59).
 */
public final class CodigosMetrica {

    private static final int DESCONOCIDOS_LISTADOS = 8;

    private CodigosMetrica() {
    }

    /**
     * El código informado que probablemente quiso ser el exigido: uno contiene al otro, comparten el primer segmento
     * («cpu_usage» y «cpu_uso») o difieren en pocas letras. Entre varios, el más cercano
     */
    public static Optional<String> parecido(String exigido, Collection<String> informados) {
        String e = normalizar(exigido);
        String segmento = primerSegmento(exigido);
        if (e.length() < 3) {
            return Optional.empty();
        }
        return informados.stream()
                .filter(i -> {
                    String n = normalizar(i);
                    return n.length() >= 3 && (n.contains(e) || e.contains(n)
                            || (segmento.length() >= 3 && segmento.equals(primerSegmento(i)))
                            || distancia(n, e) <= Math.max(2, Math.min(n.length(), e.length()) / 4));
                })
                .min(Comparator.comparingInt(i -> distancia(normalizar(i), e)));
    }

    private static String primerSegmento(String codigo) {
        String[] partes = codigo == null ? new String[0] : codigo.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        return partes.length == 0 ? "" : partes[0];
    }

    /**
     * Lo que se agrega al mensaje de las métricas faltantes: los códigos informados que no están en el catálogo y, si
     * se parecen a uno exigido, la corrección probable. Vacío si el script no informó códigos desconocidos
     */
    public static String pista(List<String> faltantes, Collection<String> desconocidos) {
        if (desconocidos.isEmpty()) {
            return "";
        }
        List<String[]> pares = faltantes.stream()
                .flatMap(f -> parecido(f, desconocidos).map(d -> new String[]{d, f}).stream())
                .toList();
        if (pares.size() == 1) {
            return ". El script informó «%s», que no está en el catálogo: ¿debía ser «%s»? Los códigos se comparan de "
                    .formatted(pares.getFirst()[0], pares.getFirst()[1]) + "forma exacta";
        }
        if (!pares.isEmpty()) {
            return ". Códigos informados que no están en el catálogo: "
                    + String.join(", ", pares.stream().map(p -> "«%s» (¿«%s»?)".formatted(p[0], p[1])).toList())
                    + ". Los códigos se comparan de forma exacta";
        }
        List<String> listados = desconocidos.stream().limit(DESCONOCIDOS_LISTADOS).map(d -> "«" + d + "»").toList();
        return ". Códigos informados que no están en el catálogo: " + String.join(", ", listados)
                + (desconocidos.size() > DESCONOCIDOS_LISTADOS ? " y otros" : "");
    }

    private static String normalizar(String codigo) {
        return codigo == null ? "" : codigo.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Distancia de Levenshtein: cuántas letras hay que cambiar, agregar o quitar */
    static int distancia(String a, String b) {
        int[] anterior = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            anterior[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cambio = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(actual[j - 1] + 1, anterior[j] + 1), anterior[j - 1] + cambio);
            }
            int[] t = anterior;
            anterior = actual;
            actual = t;
        }
        return anterior[b.length()];
    }
}
