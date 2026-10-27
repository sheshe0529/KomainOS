package com.komainos.mantenimiento;

import com.komainos.DatosPrueba;
import com.komainos.ejecucion.model.MetricaReferencia;
import com.komainos.inventario.model.ContextoEvaluacion;
import com.komainos.inventario.model.ListaReferencia;
import com.komainos.inventario.model.Metrica;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.OperadorComparacion;
import com.komainos.inventario.model.TipoDato;
import com.komainos.inventario.model.Umbral;
import com.komainos.inventario.model.UmbralAplicado;
import com.komainos.mantenimiento.service.ValoresReferencia;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cada valor de referencia se comprueba con la misma evaluación del ciclo: así el modo simulado produce exactamente el
 * nivel que se le pide con cualquier catálogo de métricas.
 */
@DisplayName("Valores de referencia para la simulación genérica (DEC-41, DEC-44)")
class ValoresReferenciaTest {

    private static final Instant AHORA = Instant.parse("2026-09-28T14:00:00Z");
    private static final ContextoEvaluacion CONTEXTO = new ContextoEvaluacion(1, null, 1, 1);

    private static Umbral numerico(Metrica m, OperadorComparacion op, String advertencia, String critico) {
        return Umbral.numerico(m, "Umbral " + m.getCodigo(), new Umbral.LimitesNumericos(op,
                advertencia == null ? null : new BigDecimal(advertencia), critico == null ? null : new BigDecimal(critico)), AHORA);
    }

    private static NivelAlerta nivel(Umbral u, MetricaReferencia.Valor v) {
        UmbralAplicado a = UmbralAplicado.global(u);
        return u.esDeLista() ? a.evaluarLista(v.elementos()).nivel() : a.evaluar(v.valor());
    }

    @Test
    @DisplayName("Mayor o igual: normal por debajo de la advertencia, advertencia y crítico en sus límites")
    void mayorIgual() {
        Metrica cpu = DatosPrueba.metrica("uso_cpu", TipoDato.DECIMAL, false);
        Umbral u = numerico(cpu, OperadorComparacion.MAYOR_IGUAL, "80", "90");

        MetricaReferencia r = ValoresReferencia.calcular(List.of(cpu), List.of(u), CONTEXTO).getFirst();

        assertThat(r.codigo()).isEqualTo("uso_cpu");
        assertThat(nivel(u, r.normal())).isEqualTo(NivelAlerta.NORMAL);
        assertThat(nivel(u, r.advertencia())).isEqualTo(NivelAlerta.ADVERTENCIA);
        assertThat(nivel(u, r.critico())).isEqualTo(NivelAlerta.CRITICO);
        assertThat(r.normal().valor()).isEqualByComparingTo("64");
    }

    @Test
    @DisplayName("Menor estricto en una métrica entera: los valores son enteros y alcanzan cada nivel")
    void menorEntero() {
        Metrica libre = DatosPrueba.metrica("espacio_libre_gb", TipoDato.INTEGER, false);
        Umbral u = numerico(libre, OperadorComparacion.MENOR, "20", "10");

        MetricaReferencia r = ValoresReferencia.calcular(List.of(libre), List.of(u), CONTEXTO).getFirst();

        assertThat(nivel(u, r.normal())).isEqualTo(NivelAlerta.NORMAL);
        assertThat(nivel(u, r.advertencia())).isEqualTo(NivelAlerta.ADVERTENCIA);
        assertThat(nivel(u, r.critico())).isEqualTo(NivelAlerta.CRITICO);
        assertThat(r.critico().valor().scale()).isZero();
    }

    @Test
    @DisplayName("Booleana con igual: 0 es normal y 1 alcanza el único nivel definido; el otro queda vacío")
    void booleana() {
        Metrica reinicio = DatosPrueba.metrica("reinicio_pendiente", TipoDato.BOOLEAN, false);
        Umbral u = numerico(reinicio, OperadorComparacion.IGUAL, "1", null);

        MetricaReferencia r = ValoresReferencia.calcular(List.of(reinicio), List.of(u), CONTEXTO).getFirst();

        assertThat(r.normal().valor()).isEqualByComparingTo("0");
        assertThat(r.advertencia().valor()).isEqualByComparingTo("1");
        assertThat(r.critico()).isNull();
    }

    @Test
    @DisplayName("Lista: normal es la lista esperada; un sobrante alcanza el nivel configurado")
    void lista() {
        Metrica puertos = DatosPrueba.metrica("puertos_permitidos", TipoDato.INTEGER, true);
        ListaReferencia lista = ListaReferencia.nueva("Puertos", TipoDato.INTEGER, false, AHORA);
        lista.reemplazarElementos(List.of("22", "443"), AHORA);
        Umbral u = Umbral.deLista(puertos, "Puertos permitidos",
                new Umbral.CriterioLista(lista, null, NivelAlerta.CRITICO), AHORA);

        MetricaReferencia r = ValoresReferencia.calcular(List.of(puertos), List.of(u), CONTEXTO).getFirst();

        assertThat(r.normal().elementos()).containsExactly("22", "443");
        assertThat(r.advertencia()).isNull();
        assertThat(nivel(u, r.critico())).isEqualTo(NivelAlerta.CRITICO);
    }

    @Test
    @DisplayName("Una métrica sin umbrales se informa con un valor neutro; las inactivas no se informan")
    void sinUmbrales() {
        Metrica carga = DatosPrueba.metrica("carga_promedio", TipoDato.DECIMAL, false);
        Metrica inactiva = DatosPrueba.metrica("retirada", TipoDato.DECIMAL, false);
        inactiva.desactivar();

        List<MetricaReferencia> r = ValoresReferencia.calcular(List.of(carga, inactiva), List.of(), CONTEXTO);

        assertThat(r).extracting(MetricaReferencia::codigo).containsExactly("carga_promedio");
        assertThat(r.getFirst().normal().valor()).isEqualByComparingTo("0");
        assertThat(r.getFirst().advertencia()).isNull();
    }
}
