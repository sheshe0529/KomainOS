package com.komainos.ejecucion.simulacion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.ejecucion.model.MedicionInformada;
import com.komainos.ejecucion.model.ParametroEjecucion;
import com.komainos.ejecucion.model.PropositoEjecucion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.ejecucion.simulacion.MetricaReferencia.Valor;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.TipoDato;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** El doble de pruebas del adaptador Ansible, del que dependen las pruebas de integración del ciclo */
@DisplayName("Doble de pruebas del adaptador Ansible (DEC-44)")
class AnsibleSimuladoTest {

    private static final List<MetricaReferencia> REFERENCIA = List.of(
            new MetricaReferencia("uso_cpu", Valor.escalar(new BigDecimal("64")), Valor.escalar(new BigDecimal("80")),
                    Valor.escalar(new BigDecimal("90"))),
            new MetricaReferencia("puertos_permitidos", Valor.lista(List.of("22", "443")), null,
                    Valor.lista(List.of("22", "443", "999999"))));

    private final ObjectMapper json = new ObjectMapper();

    private AnsibleSimulado simulador(Map<String, String> escenarios) {
        return new AnsibleSimulado(json, escenarios, s -> REFERENCIA);
    }

    private static SolicitudEjecucion solicitud(String hostname, PropositoEjecucion proposito) {
        return new SolicitudEjecucion(1, hostname, "192.0.2.10", FamiliaSistemaOperativo.LINUX, 7, proposito,
                "echo \"$DIAS\"", List.of(new ParametroEjecucion("DIAS", TipoDato.INTEGER, "7")), 5, null, List.of());
    }

    private static BigDecimal valor(ResultadoEjecucion r, String codigo) {
        return r.metricas().stream().filter(m -> m.codigo().equals(codigo)).findFirst().map(MedicionInformada::valor).orElseThrow();
    }

    @Test
    @DisplayName("Responde con los valores de referencia de cualquier métrica: previa en advertencia, posterior normal")
    void valoresGenericos() {
        AnsibleSimulado a = simulador(Map.of());

        ResultadoEjecucion previa = a.ejecutar(solicitud("SERVDESAUTV01", PropositoEjecucion.ANALISIS_PREVIO));
        ResultadoEjecucion posterior = a.ejecutar(solicitud("SERVDESAUTV01", PropositoEjecucion.ANALISIS_POSTERIOR));

        assertThat(previa.correcto()).isTrue();
        assertThat(valor(previa, "uso_cpu")).isEqualByComparingTo("80");
        assertThat(valor(posterior, "uso_cpu")).isEqualByComparingTo("64");
        // La lista no tiene nivel de advertencia: se informa la normal
        assertThat(previa.metricas().get(1).elementos()).containsExactly("22", "443");
    }

    @Test
    @DisplayName("Escenario crítico: la revisión informa los valores críticos (RF40)")
    void critico() {
        AnsibleSimulado a = simulador(Map.of("servprodbddv01", "CRITICO"));

        ResultadoEjecucion r = a.ejecutar(solicitud("SERVPRODBDDV01", PropositoEjecucion.ANALISIS_REVISION));

        assertThat(valor(r, "uso_cpu")).isEqualByComparingTo("90");
        assertThat(r.metricas().get(1).elementos()).contains("999999");
    }

    @Test
    @DisplayName("La tarea simulada informa el script preparado y sus parámetros")
    void tareaCorrecta() {
        ResultadoEjecucion r = simulador(Map.of()).ejecutar(solicitud("SERVDESAUTV01", PropositoEjecucion.TAREA));

        assertThat(r.correcto()).isTrue();
        assertThat(r.salida()).contains("tarea.sh").contains("DIAS=7").contains("sin cambios reales");
    }

    @Test
    @DisplayName("Los escenarios de fallo reproducen cada categoría de RF71 y el fallo de una tarea (RF59)")
    void escenariosDeFallo() {
        AnsibleSimulado a = simulador(Map.of("srv-conexion", "SIN_CONEXION",
                "srv-auth", "SIN_AUTENTICACION", "srv-permisos", "SIN_PERMISOS", "srv-fallo", "FALLO_TAREA"));

        assertThat(a.probarConexion(solicitud("srv-conexion", PropositoEjecucion.CONECTIVIDAD)).fallo())
                .isEqualTo(TipoFallo.COMUNICACION);
        assertThat(a.probarConexion(solicitud("srv-auth", PropositoEjecucion.CONECTIVIDAD)).fallo())
                .isEqualTo(TipoFallo.AUTENTICACION);
        assertThat(a.probarConexion(solicitud("srv-permisos", PropositoEjecucion.CONECTIVIDAD)).fallo())
                .isEqualTo(TipoFallo.PERMISOS);
        ResultadoEjecucion fallo = a.ejecutar(solicitud("srv-fallo", PropositoEjecucion.TAREA));
        assertThat(fallo.fallo()).isEqualTo(TipoFallo.EJECUCION);
        assertThat(fallo.codigoRetorno()).isEqualTo(1);
        assertThat(a.ejecutar(solicitud("srv-fallo", PropositoEjecucion.ANALISIS_PREVIO)).correcto()).isTrue();
    }
}
