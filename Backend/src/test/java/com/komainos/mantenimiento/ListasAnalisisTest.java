package com.komainos.mantenimiento;

import com.komainos.DatosPrueba;
import com.komainos.ejecucion.model.ListaEjecucion;
import com.komainos.inventario.model.ContextoEvaluacion;
import com.komainos.inventario.model.Entorno;
import com.komainos.inventario.model.ListaReferencia;
import com.komainos.inventario.model.Metrica;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.NivelCriticidad;
import com.komainos.inventario.model.ObjetivoExcepcion;
import com.komainos.inventario.model.OperadorComparacion;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.model.TipoDato;
import com.komainos.inventario.model.Umbral;
import com.komainos.mantenimiento.service.ListasAnalisis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** El análisis revisa lo que define la lista del umbral que rige para el servidor, no un parámetro del script */
@DisplayName("Listas de referencia que recibe el análisis (R2.4, R3.1)")
class ListasAnalisisTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T14:00:00Z");

    private final Entorno produccion = DatosPrueba.entorno(1, "Producción");
    private final NivelCriticidad alta = DatosPrueba.criticidad(1, "Alta", 1);
    private final Servidor web01 = DatosPrueba.servidorEn(10, "SERVPRODWEBV01", produccion, alta);
    private final Servidor web02 = DatosPrueba.servidorEn(11, "SERVPRODWEBV02", produccion, alta);
    private final Metrica archivos = DatosPrueba.metrica("arch_crit_permisos", TipoDato.STRING, true);
    private final Metrica servicios = DatosPrueba.metrica("svc_req_inactivo", TipoDato.STRING, true);
    private final Metrica cpu = DatosPrueba.metrica("cpu_uso", TipoDato.DECIMAL, false);

    private static ListaReferencia lista(String nombre, String... elementos) {
        ListaReferencia l = ListaReferencia.nueva(nombre, TipoDato.STRING, false, AHORA);
        l.reemplazarElementos(List.of(elementos), AHORA);
        return l;
    }

    /** Global con dos archivos; el servidor web01 tiene una excepción que exige solo uno */
    private Umbral permisos() {
        Umbral u = Umbral.deLista(archivos, "Permisos de archivos críticos",
                new Umbral.CriterioLista(lista("Permisos base", "/etc/passwd:644", "/etc/shadow:0"), NivelAlerta.CRITICO,
                        null), AHORA);
        u.agregarExcepcion(ObjetivoExcepcion.de(web01),
                new Umbral.ValoresExcepcion(null, null, lista("Permisos web", "/etc/passwd:644")), "Servidor web", AHORA);
        return u;
    }

    private List<ListaEjecucion> calcular(Servidor servidor) {
        Umbral numerico = Umbral.numerico(cpu, "CPU", new Umbral.LimitesNumericos(OperadorComparacion.MAYOR_IGUAL,
                new BigDecimal("80"), new BigDecimal("90")), AHORA);
        return ListasAnalisis.calcular(List.of(archivos, servicios, cpu), List.of(permisos(), numerico),
                ContextoEvaluacion.individual(servidor));
    }

    @Test
    @DisplayName("Sin excepción, el servidor recibe la lista del umbral global")
    void listaGlobal() {
        ListaEjecucion permisos = calcular(web02).getFirst();

        assertThat(permisos.codigoMetrica()).isEqualTo("arch_crit_permisos");
        assertThat(permisos.variable()).isEqualTo("KOMAINOS_LISTA_ARCH_CRIT_PERMISOS");
        assertThat(permisos.elementos()).containsExactly("/etc/passwd:644", "/etc/shadow:0");
    }

    @Test
    @DisplayName("Con una excepción para el servidor, recibe la lista de la excepción")
    void listaDeExcepcion() {
        assertThat(calcular(web01).getFirst().elementos()).containsExactly("/etc/passwd:644");
    }

    @Test
    @DisplayName("Una métrica de lista sin umbral llega vacía y las métricas de valor único no llegan")
    void soloMetricasDeLista() {
        assertThat(calcular(web02)).extracting(ListaEjecucion::codigoMetrica)
                .containsExactly("arch_crit_permisos", "svc_req_inactivo");
        assertThat(calcular(web02).getLast().elementos()).isEmpty();
    }
}
