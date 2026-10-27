package com.komainos.ejecucion.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.ejecucion.model.MedicionInformada;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.shared.exception.ReglaNegocioException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Contrato de salida de los scripts de análisis (R3.2): un objeto JSON con la lista «metricas», cada entrada con
 * «codigo», «instancia» opcional y «valor» numérico o «elementos». El objeto puede ser toda la salida o su última línea
 * que empiece por «{»: así el script puede escribir mensajes antes del resultado. Cada problema se informa con la
 * métrica que lo causa, para que quien escribe el script sepa qué corregir.
 */
public final class ContratoAnalisis {

    public static final int MAXIMO_METRICAS = 1000;
    private static final int MAXIMO_ELEMENTOS = 1000;
    private static final int LARGO_MAXIMO = 255;
    private static final String PATRON_CODIGO = "[A-Za-z0-9_.-]{1,100}";

    private ContratoAnalisis() {
    }

    /** Lectura estricta del ciclo: cualquier problema hace fallar el análisis (CP103) */
    public static List<MedicionInformada> leer(String salida, ObjectMapper json) {
        Revision revision = revisar(salida, json);
        if (!revision.errores().isEmpty()) {
            String primero = revision.errores().getFirst();
            int otros = revision.errores().size() - 1;
            throw new ReglaNegocioException("La salida del análisis no cumple el contrato JSON: " + primero
                    + (otros > 0 ? " (y %d problema%s más)".formatted(otros, otros == 1 ? "" : "s") : ""));
        }
        return revision.metricas();
    }

    /**
     * Agrega las métricas a un análisis correcto. Si la salida no cumple el contrato, el análisis falla pero conserva
     * lo que imprimió el script, que es lo que se necesita para corregirlo.
     */
    public static ResultadoEjecucion aplicar(ResultadoEjecucion resultado, ObjectMapper json) {
        if (!resultado.correcto()) {
            return resultado;
        }
        try {
            List<MedicionInformada> metricas = leer(resultado.salida(), json);
            return new ResultadoEjecucion(resultado.simulado(), resultado.codigoRetorno(), resultado.salida(), null,
                    null, metricas);
        } catch (ReglaNegocioException ex) {
            return new ResultadoEjecucion(resultado.simulado(), resultado.codigoRetorno(), resultado.salida(),
                    TipoFallo.CONTRATO.describir(ex.getMessage()), TipoFallo.CONTRATO, List.of());
        }
    }

    /** Revisión completa: devuelve las métricas válidas y todos los problemas encontrados */
    public static Revision revisar(String salida, ObjectMapper json) {
        List<String> errores = new ArrayList<>();
        JsonNode raiz = localizar(salida, json);
        if (raiz == null) {
            errores.add("no se encontró un objeto JSON con la lista «metricas» en la salida");
            return new Revision(List.of(), errores);
        }
        JsonNode lista = raiz.path("metricas");
        if (!lista.isArray()) {
            errores.add("«metricas» debe ser una lista");
            return new Revision(List.of(), errores);
        }
        if (lista.isEmpty()) {
            errores.add("la lista «metricas» está vacía");
        }
        if (lista.size() > MAXIMO_METRICAS) {
            errores.add("la salida supera las %d métricas".formatted(MAXIMO_METRICAS));
            return new Revision(List.of(), errores);
        }
        List<MedicionInformada> metricas = new ArrayList<>();
        Set<String> vistas = new HashSet<>();
        int posicion = 0;
        for (JsonNode m : lista) {
            posicion++;
            leerMetrica(m, posicion, vistas, errores).ifPresent(metricas::add);
        }
        return new Revision(List.copyOf(metricas), List.copyOf(errores));
    }

    private static Optional<MedicionInformada> leerMetrica(JsonNode m, int posicion, Set<String> vistas,
                                                                     List<String> errores) {
        if (!m.isObject()) {
            errores.add("la entrada %d no es un objeto".formatted(posicion));
            return Optional.empty();
        }
        String codigo = m.path("codigo").isTextual() ? m.path("codigo").asText() : "";
        if (!codigo.matches(PATRON_CODIGO)) {
            errores.add(("la entrada %d no tiene un «codigo» válido (letras, números, punto, guion o guion bajo, "
                    + "hasta 100 caracteres)").formatted(posicion));
            return Optional.empty();
        }
        String instancia = m.hasNonNull("instancia") ? m.path("instancia").asText() : "";
        String nombre = instancia.isEmpty() ? "«%s»".formatted(codigo) : "«%s» (%s)".formatted(codigo, instancia);
        if (instancia.length() > LARGO_MAXIMO) {
            errores.add("la instancia de %s supera los %d caracteres".formatted(nombre, LARGO_MAXIMO));
            return Optional.empty();
        }
        if (!vistas.add(codigo + "\u0000" + instancia)) {
            errores.add("la métrica %s aparece más de una vez".formatted(nombre));
            return Optional.empty();
        }
        boolean tieneValor = m.has("valor");
        boolean tieneElementos = m.has("elementos");
        if (tieneValor == tieneElementos) {
            errores.add(tieneValor
                    ? "la métrica %s informa «valor» y «elementos»: use solo uno".formatted(nombre)
                    : "a la métrica %s le falta «valor» o «elementos»".formatted(nombre));
            return Optional.empty();
        }
        if (tieneValor) {
            BigDecimal valor = numero(m.path("valor"));
            if (valor == null) {
                errores.add("el «valor» de %s debe ser numérico o booleano (se recibió %s)"
                        .formatted(nombre, recortar(m.path("valor").toString())));
                return Optional.empty();
            }
            return Optional.of(new MedicionInformada(codigo, instancia, valor, null));
        }
        JsonNode elementos = m.path("elementos");
        if (!elementos.isArray() || elementos.size() > MAXIMO_ELEMENTOS) {
            errores.add("«elementos» de %s debe ser una lista de hasta %d elementos".formatted(nombre, MAXIMO_ELEMENTOS));
            return Optional.empty();
        }
        List<String> valores = new ArrayList<>();
        for (JsonNode e : elementos) {
            if (!e.isValueNode() || e.isNull() || e.asText().length() > LARGO_MAXIMO) {
                errores.add("«elementos» de %s solo admite textos o números de hasta %d caracteres"
                        .formatted(nombre, LARGO_MAXIMO));
                return Optional.empty();
            }
            valores.add(e.asText());
        }
        return Optional.of(new MedicionInformada(codigo, instancia, null, List.copyOf(valores)));
    }

    /** Un número, un booleano (1 o 0) o un texto numérico como "58.2", frecuente en Bash */
    private static BigDecimal numero(JsonNode valor) {
        if (valor.isNumber()) {
            return valor.decimalValue();
        }
        if (valor.isBoolean()) {
            return valor.asBoolean() ? BigDecimal.ONE : BigDecimal.ZERO;
        }
        if (valor.isTextual()) {
            try {
                return new BigDecimal(valor.asText().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private static JsonNode localizar(String salida, ObjectMapper json) {
        if (salida == null || salida.isBlank()) {
            return null;
        }
        JsonNode completa = objeto(salida.trim(), json);
        if (completa != null && completa.has("metricas")) {
            return completa;
        }
        List<String> lineas = salida.lines().toList();
        for (int i = lineas.size() - 1; i >= 0; i--) {
            String linea = lineas.get(i).trim();
            if (linea.startsWith("{")) {
                JsonNode nodo = objeto(linea, json);
                if (nodo != null && nodo.has("metricas")) {
                    return nodo;
                }
            }
        }
        return null;
    }

    private static JsonNode objeto(String texto, ObjectMapper json) {
        try {
            JsonNode nodo = json.readTree(texto);
            return nodo != null && nodo.isObject() ? nodo : null;
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static String recortar(String texto) {
        return texto.length() > 40 ? texto.substring(0, 40) + "…" : texto;
    }

    /** Métricas válidas y problemas de la salida; sin problemas, la salida cumple el contrato */
    public record Revision(List<MedicionInformada> metricas, List<String> errores) {

        public boolean valida() {
            return errores.isEmpty();
        }
    }
}
