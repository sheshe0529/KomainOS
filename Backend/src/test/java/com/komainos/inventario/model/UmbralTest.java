package com.komainos.inventario.model;

import com.komainos.DatosPrueba;
import com.komainos.inventario.model.Umbral.CriterioLista;
import com.komainos.inventario.model.Umbral.LimitesNumericos;
import com.komainos.inventario.model.Umbral.ValoresExcepcion;
import com.komainos.shared.exception.ConflictoException;
import com.komainos.shared.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Umbral: modalidad numérica o de lista y excepciones (RF16, HU12, R2.4)")
class UmbralTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");

    private static BigDecimal n(String valor) {
        return valor == null ? null : new BigDecimal(valor);
    }

    private static LimitesNumericos limites(OperadorComparacion operador, String advertencia, String critico) {
        return new LimitesNumericos(operador, n(advertencia), n(critico));
    }

    private static ListaReferencia lista(String nombre, TipoDato tipo, String... elementos) {
        ListaReferencia lista = ListaReferencia.nueva(nombre, tipo, false, AHORA);
        lista.reemplazarElementos(List.of(elementos), AHORA);
        return lista;
    }

    @Nested
    @DisplayName("numérico")
    class Numerico {

        private final Metrica disco = DatosPrueba.metrica("disco_uso_pct", TipoDato.DECIMAL, false);

        @Test
        @DisplayName("CP045: con mayor o igual el crítico es el mayor; con menor o igual, el menor")
        void operadoresDistintos() {
            Umbral uso = Umbral.numerico(disco, "Disco lleno", limites(OperadorComparacion.MAYOR_IGUAL, "80", "90"), AHORA);
            Umbral libre = Umbral.numerico(DatosPrueba.metrica("disco_libre_gb", TipoDato.DECIMAL, false), "Poco espacio",
                    limites(OperadorComparacion.MENOR_IGUAL, "20", "10"), AHORA);

            assertThat(uso.isActivo()).isTrue();
            assertThat(uso.esDeLista()).isFalse();
            assertThat(libre.getValorAdvertencia()).isEqualByComparingTo("20");
            assertThat(libre.getValorCritico()).isEqualByComparingTo("10");
        }

        @Test
        @DisplayName("CP046: valores incoherentes con el operador se rechazan")
        void incoherente() {
            assertThatThrownBy(() -> Umbral.numerico(disco, "Memoria alta",
                    limites(OperadorComparacion.MAYOR_IGUAL, "90", "80"), AHORA))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageContaining("no son coherentes con el operador");
        }

        @Test
        @DisplayName("basta uno de los dos valores, pero no ninguno; el operador es obligatorio")
        void valoresMinimos() {
            Umbral solo = Umbral.numerico(disco, "Solo crítico", limites(OperadorComparacion.MAYOR, null, "95"), AHORA);
            assertThat(solo.getValorAdvertencia()).isNull();
            assertThatThrownBy(() -> Umbral.numerico(disco, "Vacío", limites(OperadorComparacion.MAYOR, null, null), AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("advertencia, el crítico o ambos");
            assertThatThrownBy(() -> Umbral.numerico(disco, "Sin operador", limites(null, "1", "2"), AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("operador");
        }

        @Test
        @DisplayName("una métrica de lista no admite umbral numérico ni una métrica inactiva umbrales nuevos")
        void modalidad() {
            Metrica puertos = DatosPrueba.metrica("puertos", TipoDato.INTEGER, true);
            assertThatThrownBy(() -> Umbral.numerico(puertos, "X", limites(OperadorComparacion.MAYOR, "1", "2"), AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("es de lista");
            disco.desactivar();
            assertThatThrownBy(() -> Umbral.numerico(disco, "X", limites(OperadorComparacion.MAYOR, "1", "2"), AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("inactiva");
        }

        @Test
        @DisplayName("CP198: el operador no cambia mientras el umbral tenga excepciones")
        void operadorConExcepciones() {
            Umbral umbral = Umbral.numerico(disco, "Disco", limites(OperadorComparacion.MAYOR, "80", "90"), AHORA);
            umbral.agregarExcepcion(ObjetivoExcepcion.de(DatosPrueba.entorno(1, "Producción")),
                    new ValoresExcepcion(n("70"), n("85"), null), null, AHORA);

            assertThatThrownBy(() -> umbral.actualizarNumerico("Disco", limites(OperadorComparacion.MENOR, "80", "70"), AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("antes de cambiar su operador");
            umbral.actualizarNumerico("Disco lleno", limites(OperadorComparacion.MAYOR, "75", "95"), AHORA);
            assertThat(umbral.getNombre()).isEqualTo("Disco lleno");
        }
    }

    @Nested
    @DisplayName("de lista")
    class DeLista {

        private final Metrica puertos = DatosPrueba.metrica("puertos_escucha", TipoDato.INTEGER, true);
        private final ListaReferencia web = lista("Puertos web", TipoDato.INTEGER, "22", "80", "443");

        @Test
        @DisplayName("CP191: define la lista y el nivel para faltantes y sobrantes")
        void valido() {
            Umbral umbral = Umbral.deLista(puertos, "Puertos esperados",
                    new CriterioLista(web, NivelAlerta.CRITICO, NivelAlerta.ADVERTENCIA), AHORA);

            assertThat(umbral.esDeLista()).isTrue();
            assertThat(umbral.getOperador()).isNull();
            assertThat(umbral.getLista()).isSameAs(web);
        }

        @Test
        @DisplayName("CP192: la lista es del tipo de la métrica, con al menos un nivel y ninguno normal")
        void invalidos() {
            ListaReferencia servicios = lista("Servicios", TipoDato.STRING, "sshd");
            assertThatThrownBy(() -> Umbral.deLista(puertos, "X", new CriterioLista(servicios, NivelAlerta.CRITICO, null), AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("es de tipo texto");
            assertThatThrownBy(() -> Umbral.deLista(puertos, "X", new CriterioLista(web, null, null), AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("faltantes");
            assertThatThrownBy(() -> Umbral.deLista(puertos, "X", new CriterioLista(web, NivelAlerta.NORMAL, null), AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("advertencia o crítico");
            assertThatThrownBy(() -> Umbral.deLista(puertos, "X", new CriterioLista(null, NivelAlerta.CRITICO, null), AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("lista de referencia");
        }

        @Test
        @DisplayName("CP190: una lista inactiva se conserva donde ya está, pero no se asigna")
        void listaInactiva() {
            Umbral umbral = Umbral.deLista(puertos, "Puertos", new CriterioLista(web, NivelAlerta.CRITICO, null), AHORA);
            web.desactivar();

            umbral.actualizarLista("Puertos esperados", new CriterioLista(web, NivelAlerta.CRITICO, NivelAlerta.CRITICO), AHORA);
            assertThat(umbral.getNivelSobrante()).isEqualTo(NivelAlerta.CRITICO);
            ListaReferencia otra = lista("Puertos BD", TipoDato.INTEGER, "5432");
            otra.desactivar();
            assertThatThrownBy(() -> umbral.actualizarLista("Puertos", new CriterioLista(otra, NivelAlerta.CRITICO, null), AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("inactiva");
        }
    }

    @Nested
    @DisplayName("excepciones")
    class Excepciones {

        private final Metrica cpu = DatosPrueba.metrica("cpu_uso_pct", TipoDato.DECIMAL, false);
        private final Entorno produccion = DatosPrueba.entorno(1, "Producción");

        @Test
        @DisplayName("CP195: una sola excepción por alcance")
        void unaPorAlcance() {
            Umbral umbral = Umbral.numerico(cpu, "CPU alta", limites(OperadorComparacion.MAYOR, "80", "90"), AHORA);
            umbral.agregarExcepcion(ObjetivoExcepcion.de(produccion), new ValoresExcepcion(n("70"), n("85"), null), null, AHORA);

            assertThatThrownBy(() -> umbral.agregarExcepcion(ObjetivoExcepcion.de(produccion),
                    new ValoresExcepcion(n("60"), n("75"), null), null, AHORA))
                    .isInstanceOf(ConflictoException.class).hasMessageContaining("Producción");
            // Otro alcance sobre el mismo id sí se admite
            umbral.agregarExcepcion(ObjetivoExcepcion.de(DatosPrueba.criticidad(1, "Alta", 1)),
                    new ValoresExcepcion(n("75"), n("88"), null), null, AHORA);
            assertThat(umbral.getExcepciones()).hasSize(2);
        }

        @Test
        @DisplayName("CP195: los valores de la excepción son coherentes con el operador del umbral")
        void coherente() {
            Umbral umbral = Umbral.numerico(cpu, "CPU alta", limites(OperadorComparacion.MAYOR, "80", "90"), AHORA);
            assertThatThrownBy(() -> umbral.agregarExcepcion(ObjetivoExcepcion.de(produccion),
                    new ValoresExcepcion(n("90"), n("70"), null), null, AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("coherentes");
            assertThatThrownBy(() -> umbral.agregarExcepcion(ObjetivoExcepcion.de(produccion),
                    new ValoresExcepcion(null, null, null), null, AHORA))
                    .isInstanceOf(ReglaNegocioException.class);
            assertThatThrownBy(() -> umbral.agregarExcepcion(ObjetivoExcepcion.de(produccion),
                    new ValoresExcepcion(n("70"), n("80"), lista("L", TipoDato.DECIMAL, "1")), null, AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no lleva lista");
        }

        @Test
        @DisplayName("en un umbral de lista la excepción cambia solo la lista, del mismo tipo")
        void deLista() {
            Metrica usuarios = DatosPrueba.metrica("usuarios_locales", TipoDato.STRING, true);
            Umbral umbral = Umbral.deLista(usuarios, "Usuarios autorizados",
                    new CriterioLista(lista("Base", TipoDato.STRING, "root"), null, NivelAlerta.CRITICO), AHORA);

            UmbralExcepcion excepcion = umbral.agregarExcepcion(ObjetivoExcepcion.de(produccion),
                    new ValoresExcepcion(null, null, lista("Producción", TipoDato.STRING, "root", "deploy")), "Despliegues", AHORA);
            assertThat(excepcion.getLista().elementosVigentes()).containsExactly("deploy", "root");
            assertThat(excepcion.alcance()).isEqualTo(AlcanceExcepcion.ENTORNO);

            assertThatThrownBy(() -> umbral.agregarExcepcion(ObjetivoExcepcion.de(DatosPrueba.criticidad(1, "Alta", 1)),
                    new ValoresExcepcion(n("1"), null, null), null, AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("solo cambia la lista");
            assertThatThrownBy(() -> umbral.agregarExcepcion(ObjetivoExcepcion.de(DatosPrueba.criticidad(1, "Alta", 1)),
                    new ValoresExcepcion(null, null, lista("Puertos", TipoDato.INTEGER, "22")), null, AHORA))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("es de tipo entero");
        }
    }
}
