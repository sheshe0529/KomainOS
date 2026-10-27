package com.komainos.ejecucion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.ejecucion.model.MedicionInformada;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.ejecucion.service.ContratoAnalisis;
import com.komainos.shared.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Contrato JSON de los scripts de análisis (R3.2, CP103)")
class ContratoAnalisisTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("Toma la última línea JSON aunque el script escriba mensajes antes")
    void ultimaLinea() {
        String salida = """
                Análisis de SERVDESAUTV01 completado
                {"metricas":[{"codigo":"cpu_uso_pct","valor":58.5},{"codigo":"disco_uso_pct","instancia":"/","valor":"86"},
                {"codigo":"x"}]}
                {"metricas":[{"codigo":"cpu_uso_pct","valor":58.5},{"codigo":"disco_uso_pct","instancia":"/","valor":"86"},{"codigo":"reinicio_pendiente","valor":true},{"codigo":"puertos","elementos":[22,"443"]}]}
                """;

        List<MedicionInformada> metricas = ContratoAnalisis.leer(salida, json);

        assertThat(metricas).extracting(MedicionInformada::codigo)
                .containsExactly("cpu_uso_pct", "disco_uso_pct", "reinicio_pendiente", "puertos");
        assertThat(metricas.get(1).valor()).isEqualByComparingTo("86");
        assertThat(metricas.get(1).instancia()).isEqualTo("/");
        assertThat(metricas.get(2).valor()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(metricas.get(3).elementos()).containsExactly("22", "443");
    }

    @Test
    @DisplayName("Informa cada problema con la métrica que lo causa")
    void problemasDetallados() {
        ContratoAnalisis.Revision revision = ContratoAnalisis.revisar("""
                {"metricas":[{"codigo":"cpu","valor":"58%"},{"codigo":"cpu","valor":1},{"codigo":"mem"},
                {"codigo":"disco","valor":1,"elementos":[]},{"valor":3}]}""", json);

        assertThat(revision.valida()).isFalse();
        assertThat(revision.errores()).hasSize(5);
        assertThat(revision.errores().get(0)).contains("«cpu»").contains("numérico");
        assertThat(revision.errores().get(1)).contains("más de una vez");
        assertThat(revision.errores().get(2)).contains("«mem»");
        assertThat(revision.errores().get(3)).contains("use solo uno");
        assertThat(revision.errores().get(4)).contains("entrada 5");
    }

    @Test
    @DisplayName("Sin objeto con «metricas» o con la lista vacía, el análisis no es concluyente")
    void sinMetricas() {
        for (String salida : new String[]{"", "Todo bien", "{}", "{\"metricas\":[]}", "{\"metricas\":{}}"}) {
            assertThatThrownBy(() -> ContratoAnalisis.leer(salida, json))
                    .isInstanceOf(ReglaNegocioException.class)
                    .hasMessageStartingWith("La salida del análisis no cumple el contrato JSON");
        }
    }

    @Test
    @DisplayName("Un análisis que no cumple el contrato falla como CONTRATO y conserva la salida para corregirlo")
    void aplicarConservaSalida() {
        ResultadoEjecucion r = ContratoAnalisis.aplicar(ResultadoEjecucion.exito(false, "sin json", List.of()), json);

        assertThat(r.correcto()).isFalse();
        assertThat(r.fallo()).isEqualTo(TipoFallo.CONTRATO);
        assertThat(r.salida()).isEqualTo("sin json");
        assertThat(r.error()).startsWith("Salida no válida:");
    }
}
