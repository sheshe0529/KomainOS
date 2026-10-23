package com.komainos.inventario.model;

import com.komainos.DatosPrueba;
import com.komainos.inventario.model.Umbral.CriterioLista;
import com.komainos.inventario.model.Umbral.LimitesNumericos;
import com.komainos.inventario.model.Umbral.ValoresExcepcion;
import com.komainos.inventario.model.UmbralAplicado.ResultadoLista;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Regla de resolución y evaluación de umbrales: servidor > grupo > entorno > criticidad > global (R2.4)")
class UmbralAplicadoTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");

    private final Entorno produccion = DatosPrueba.entorno(1, "Producción");
    private final Entorno desarrollo = DatosPrueba.entorno(2, "Desarrollo");
    private final NivelCriticidad alta = DatosPrueba.criticidad(1, "Alta", 1);
    private final GrupoMantenimiento grupoBd = DatosPrueba.grupo(7, "Clúster BD");
    private final Servidor bd01 = DatosPrueba.servidorEn(10, "SERVPRODBDDV01", produccion, alta);
    private final Servidor bd02 = DatosPrueba.servidorEn(11, "SERVPRODBDDV02", produccion, alta);
    private final Servidor des01 = DatosPrueba.servidorEn(12, "SERVDESAPPV01", desarrollo, DatosPrueba.criticidad(3, "Baja", 3));

    private static BigDecimal n(String valor) {
        return valor == null ? null : new BigDecimal(valor);
    }

    private static ValoresExcepcion valores(String advertencia, String critico) {
        return new ValoresExcepcion(n(advertencia), n(critico), null);
    }

    /** Global 80/90 con una excepción en cada alcance */
    private Umbral cpuConExcepciones() {
        Umbral u = Umbral.numerico(DatosPrueba.metrica("cpu_uso_pct", TipoDato.DECIMAL, false), "CPU alta",
                new LimitesNumericos(OperadorComparacion.MAYOR, n("80"), n("90")), AHORA);
        u.agregarExcepcion(ObjetivoExcepcion.de(alta), valores("75", "85"), null, AHORA);
        u.agregarExcepcion(ObjetivoExcepcion.de(produccion), valores("70", "80"), null, AHORA);
        u.agregarExcepcion(ObjetivoExcepcion.de(grupoBd), valores("65", "75"), null, AHORA);
        u.agregarExcepcion(ObjetivoExcepcion.de(bd01), valores("60", "70"), "Réplica con carga de reportes", AHORA);
        return u;
    }

    @Nested
    @DisplayName("resolución")
    class Resolucion {

        @Test
        @DisplayName("CP193: el servidor manda sobre el grupo, el entorno y la criticidad")
        void servidorPrimero() {
            UmbralAplicado a = UmbralAplicado.resolver(cpuConExcepciones(), ContextoEvaluacion.enGrupo(bd01, 7));

            assertThat(a.origen()).isEqualTo(OrigenUmbral.SERVIDOR);
            assertThat(a.advertencia()).isEqualByComparingTo("60");
            assertThat(a.critico()).isEqualByComparingTo("70");
            assertThat(a.operador()).isEqualTo(OperadorComparacion.MAYOR);
            assertThat(a.excepcion().getMotivo()).isEqualTo("Réplica con carga de reportes");
        }

        @Test
        @DisplayName("CP194: el grupo aplica solo en una orden de ese grupo; fuera de ella, el entorno")
        void grupoComoContexto() {
            Umbral u = cpuConExcepciones();

            assertThat(UmbralAplicado.resolver(u, ContextoEvaluacion.enGrupo(bd02, 7)).origen()).isEqualTo(OrigenUmbral.GRUPO);
            assertThat(UmbralAplicado.resolver(u, ContextoEvaluacion.individual(bd02)).origen()).isEqualTo(OrigenUmbral.ENTORNO);
            // Otro grupo en ejecución, sin excepción propia, no aporta nada
            assertThat(UmbralAplicado.resolver(u, ContextoEvaluacion.enGrupo(bd02, 99)).origen()).isEqualTo(OrigenUmbral.ENTORNO);
        }

        @Test
        @DisplayName("CP193: sin excepción de servidor, grupo ni entorno, la criticidad; y si no, el global")
        void criticidadYGlobal() {
            Umbral u = Umbral.numerico(DatosPrueba.metrica("cpu_uso_pct", TipoDato.DECIMAL, false), "CPU alta",
                    new LimitesNumericos(OperadorComparacion.MAYOR, n("80"), n("90")), AHORA);
            u.agregarExcepcion(ObjetivoExcepcion.de(alta), valores("75", "85"), null, AHORA);

            assertThat(UmbralAplicado.resolver(u, ContextoEvaluacion.individual(bd01)).origen())
                    .isEqualTo(OrigenUmbral.CRITICIDAD);
            UmbralAplicado global = UmbralAplicado.resolver(u, ContextoEvaluacion.individual(des01));
            assertThat(global.origen()).isEqualTo(OrigenUmbral.GLOBAL);
            assertThat(global.excepcion()).isNull();
            assertThat(global.advertencia()).isEqualByComparingTo("80");
        }

        @Test
        @DisplayName("CP196: una excepción desactivada se ignora y se aplica la siguiente en prioridad")
        void desactivada() {
            Umbral u = cpuConExcepciones();
            u.getExcepciones().stream().filter(e -> e.alcance() == AlcanceExcepcion.SERVIDOR).findFirst().orElseThrow()
                    .desactivar(AHORA);

            UmbralAplicado a = UmbralAplicado.resolver(u, ContextoEvaluacion.individual(bd01));
            assertThat(a.origen()).isEqualTo(OrigenUmbral.ENTORNO);
            assertThat(a.critico()).isEqualByComparingTo("80");
        }

        @Test
        @DisplayName("la excepción reemplaza ambos valores: un nivel que no define no se evalúa")
        void reemplazaValores() {
            Umbral u = Umbral.numerico(DatosPrueba.metrica("cpu_uso_pct", TipoDato.DECIMAL, false), "CPU alta",
                    new LimitesNumericos(OperadorComparacion.MAYOR, n("80"), n("90")), AHORA);
            u.agregarExcepcion(ObjetivoExcepcion.de(produccion), valores(null, "95"), null, AHORA);

            UmbralAplicado a = UmbralAplicado.resolver(u, ContextoEvaluacion.individual(bd01));
            assertThat(a.advertencia()).isNull();
            assertThat(a.evaluar(n("85"))).isEqualTo(NivelAlerta.NORMAL);
            assertThat(a.evaluar(n("96"))).isEqualTo(NivelAlerta.CRITICO);
        }
    }

    @Nested
    @DisplayName("evaluación numérica")
    class Numerica {

        private final UmbralAplicado cpu = UmbralAplicado.global(Umbral.numerico(
                DatosPrueba.metrica("cpu_uso_pct", TipoDato.DECIMAL, false), "CPU alta",
                new LimitesNumericos(OperadorComparacion.MAYOR_IGUAL, n("80"), n("90")), AHORA));
        private final UmbralAplicado libre = UmbralAplicado.global(Umbral.numerico(
                DatosPrueba.metrica("disco_libre_gb", TipoDato.DECIMAL, false), "Poco espacio",
                new LimitesNumericos(OperadorComparacion.MENOR_IGUAL, n("20"), n("10")), AHORA));

        @ParameterizedTest(name = "CPU {0} → {1}")
        @CsvSource({"79.9, NORMAL", "80, ADVERTENCIA", "89.99, ADVERTENCIA", "90, CRITICO", "100, CRITICO"})
        @DisplayName("CP197: con mayor o igual, desde la advertencia y luego el crítico")
        void mayorIgual(BigDecimal medicion, NivelAlerta esperado) {
            assertThat(cpu.evaluar(medicion)).isEqualTo(esperado);
        }

        @ParameterizedTest(name = "Libre {0} GB → {1}")
        @CsvSource({"25, NORMAL", "20, ADVERTENCIA", "10, CRITICO", "3, CRITICO"})
        @DisplayName("CP197: con menor o igual se evalúa hacia abajo")
        void menorIgual(BigDecimal medicion, NivelAlerta esperado) {
            assertThat(libre.evaluar(medicion)).isEqualTo(esperado);
        }
    }

    @Nested
    @DisplayName("evaluación de lista")
    class DeLista {

        private ListaReferencia lista(String nombre, TipoDato tipo, boolean ignora, String... elementos) {
            ListaReferencia l = ListaReferencia.nueva(nombre, tipo, ignora, AHORA);
            l.reemplazarElementos(List.of(elementos), AHORA);
            return l;
        }

        private UmbralAplicado puertos(NivelAlerta faltante, NivelAlerta sobrante) {
            return UmbralAplicado.global(Umbral.deLista(DatosPrueba.metrica("puertos_escucha", TipoDato.INTEGER, true),
                    "Puertos esperados", new CriterioLista(lista("Puertos web", TipoDato.INTEGER, false, "22", "80", "443"),
                            faltante, sobrante), AHORA));
        }

        @Test
        @DisplayName("CP197: si coincide es normal")
        void normal() {
            ResultadoLista r = puertos(NivelAlerta.CRITICO, NivelAlerta.ADVERTENCIA).evaluarLista(List.of("443", "80", "022"));
            assertThat(r.nivel()).isEqualTo(NivelAlerta.NORMAL);
            assertThat(r.faltantes()).isEmpty();
            assertThat(r.sobrantes()).isEmpty();
        }

        @Test
        @DisplayName("CP197: falta y sobra a la vez: el resultado es el peor de los dos")
        void faltaYSobra() {
            ResultadoLista r = puertos(NivelAlerta.ADVERTENCIA, NivelAlerta.CRITICO).evaluarLista(List.of("22", "80", "3306"));
            assertThat(r.faltantes()).containsExactly("443");
            assertThat(r.sobrantes()).containsExactly("3306");
            assertThat(r.nivel()).isEqualTo(NivelAlerta.CRITICO);
        }

        @Test
        @DisplayName("una diferencia sin nivel definido no cuenta, aunque se informe")
        void nivelNoDefinido() {
            ResultadoLista r = puertos(NivelAlerta.CRITICO, null).evaluarLista(List.of("22", "80", "443", "8080"));
            assertThat(r.sobrantes()).containsExactly("8080");
            assertThat(r.nivel()).isEqualTo(NivelAlerta.NORMAL);
        }

        @Test
        @DisplayName("CP187: una lista que no distingue mayúsculas compara sin distinguirlas")
        void sinMayusculas() {
            UmbralAplicado servicios = UmbralAplicado.global(Umbral.deLista(
                    DatosPrueba.metrica("servicios_ejecucion", TipoDato.STRING, true), "Servicios obligatorios",
                    new CriterioLista(lista("Servicios base", TipoDato.STRING, true, "crond", "sshd"), NivelAlerta.CRITICO, null),
                    AHORA));
            assertThat(servicios.evaluarLista(List.of("SSHD", "Crond", "rsyslog")).nivel()).isEqualTo(NivelAlerta.NORMAL);
            assertThat(servicios.evaluarLista(List.of("sshd")).faltantes()).containsExactly("crond");
        }

        @Test
        @DisplayName("la excepción de un umbral de lista cambia la lista y conserva los niveles del umbral")
        void excepcionDeLista() {
            Umbral u = Umbral.deLista(DatosPrueba.metrica("puertos_escucha", TipoDato.INTEGER, true), "Puertos",
                    new CriterioLista(lista("Puertos web", TipoDato.INTEGER, false, "22", "80", "443"), NivelAlerta.CRITICO,
                            NivelAlerta.ADVERTENCIA), AHORA);
            u.agregarExcepcion(ObjetivoExcepcion.de(bd01),
                    new ValoresExcepcion(null, null, lista("Puertos BD", TipoDato.INTEGER, false, "22", "5432")), null, AHORA);

            UmbralAplicado a = UmbralAplicado.resolver(u, ContextoEvaluacion.individual(bd01));
            assertThat(a.origen()).isEqualTo(OrigenUmbral.SERVIDOR);
            assertThat(a.lista().getNombre()).isEqualTo("Puertos BD");
            assertThat(a.nivelSobrante()).isEqualTo(NivelAlerta.ADVERTENCIA);
            assertThat(a.evaluarLista(List.of("22", "5432", "80")).nivel()).isEqualTo(NivelAlerta.ADVERTENCIA);
        }
    }
}
