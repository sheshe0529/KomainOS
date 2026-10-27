package com.komainos.mantenimiento.model;

import com.komainos.DatosPrueba;
import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.shared.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Las reglas que viven en las entidades del ciclo: no se pueden saltar desde otro punto del código */
@DisplayName("Entidades del ciclo de mantenimiento")
class CicloModeloTest {

    private static final Instant AHORA = Instant.parse("2026-09-28T14:00:00Z");
    private static final Usuario RESPONSABLE = DatosPrueba.usuario(2, "resp", Rol.RESPONSABLE);

    private static ReglaPoliticaCambio regla(boolean responsable, boolean administrador) {
        ReglaPoliticaCambio r = ReglaPoliticaCambio.nueva(DatosPrueba.entorno(1, "Producción"), NivelAfectacion.TOTAL);
        r.configurar(responsable, administrador, true, AHORA);
        return r;
    }

    @Nested
    @DisplayName("Ronda de autorización (RF44, RF48, RF75)")
    class Autorizacion {

        @Test
        @DisplayName("Sin aprobadores la ronda es automática y queda aprobada (CP116)")
        void automatica() {
            SolicitudAutorizacion s = SolicitudAutorizacion.abrir(null, regla(false, false), 1, AHORA.plusSeconds(3600), AHORA);

            assertThat(s.esAutomatica()).isTrue();
            assertThat(s.aprobada()).isTrue();
            assertThat(s.pendienteActual()).isEmpty();
        }

        @Test
        @DisplayName("Primero el responsable y solo después el administrador, con un único plazo (CP113, CP126)")
        void secuencia() {
            SolicitudAutorizacion s = SolicitudAutorizacion.abrir(null, regla(true, true), 1, AHORA.plusSeconds(3600), AHORA);

            assertThat(s.getDecisiones()).extracting(AutorizacionDetalle::getTipo)
                    .containsExactly(TipoAprobador.RESPONSABLE, TipoAprobador.ADMINISTRADOR);
            assertThat(s.pendienteActual().orElseThrow().getTipo()).isEqualTo(TipoAprobador.RESPONSABLE);
            s.pendienteActual().orElseThrow().decidir(EstadoAutorizacion.APROBADA, RESPONSABLE, "Conforme", null, AHORA);
            assertThat(s.pendienteActual().orElseThrow().getTipo()).isEqualTo(TipoAprobador.ADMINISTRADOR);
            assertThat(s.aprobada()).isFalse();
            assertThat(s.vencida(AHORA.plusSeconds(3599))).isFalse();
            assertThat(s.vencida(AHORA.plusSeconds(3600))).isTrue();
        }

        @Test
        @DisplayName("Solo el responsable propone una fecha y debe ser futura; mientras tanto nadie más decide")
        void propuesta() {
            SolicitudAutorizacion s = SolicitudAutorizacion.abrir(null, regla(true, true), 1, AHORA.plusSeconds(3600), AHORA);
            AutorizacionDetalle responsable = s.pendienteActual().orElseThrow();

            assertThatThrownBy(() -> responsable.decidir(EstadoAutorizacion.FECHA_PROPUESTA, RESPONSABLE, "Otra",
                    AHORA.minusSeconds(60), AHORA)).isInstanceOf(ReglaNegocioException.class);
            responsable.decidir(EstadoAutorizacion.FECHA_PROPUESTA, RESPONSABLE, "Otra fecha", AHORA.plusSeconds(86400), AHORA);

            assertThat(s.propuesta()).isPresent();
            assertThat(s.pendienteActual()).isEmpty();
            assertThatThrownBy(() -> s.getDecisiones().getLast().decidir(EstadoAutorizacion.FECHA_PROPUESTA,
                    null, "x", AHORA.plusSeconds(90000), AHORA)).isInstanceOf(ReglaNegocioException.class);
        }

        @Test
        @DisplayName("Al vencer, las decisiones pendientes quedan vencidas sin usuario (RF50)")
        void vencimiento() {
            SolicitudAutorizacion s = SolicitudAutorizacion.abrir(null, regla(true, true), 1, AHORA, AHORA);

            s.vencer(AHORA);

            assertThat(s.getDecisiones()).allMatch(d -> d.getEstado() == EstadoAutorizacion.VENCIDA
                    && d.getUsuarioDecision() == null);
        }
    }

    @Nested
    @DisplayName("Validación funcional (RF57)")
    class Funcional {

        @Test
        @DisplayName("Se responde dentro del plazo; vencida no presume conformidad")
        void plazo() {
            ValidacionFuncional v = ValidacionFuncional.solicitar(null, RESPONSABLE, AHORA, AHORA.plusSeconds(3600));

            assertThatThrownBy(() -> v.responder(true, "Tarde", AHORA.plusSeconds(3600)))
                    .hasMessageContaining("venció");
            v.vencer(AHORA.plusSeconds(3600));

            assertThat(v.getEstado()).isEqualTo(EstadoValidacionFuncional.VENCIDA);
            assertThat(v.esConforme()).isFalse();
        }
    }

    @Nested
    @DisplayName("Incidencia (RF61)")
    class IncidenciaAtencion {

        @Test
        @DisplayName("No se cierra sin diagnóstico ni resultado (CP150); en atención sí puede quedar incompleta")
        void cierreExigeDiagnostico() {
            Incidencia i = Incidencia.abrir(null, OrigenIncidencia.FALLO_EJECUCION, "rc=1", AHORA);

            assertThatThrownBy(() -> i.atender(null, " ", "Revisado", "", true, AHORA))
                    .hasMessageContaining("diagnóstico");
            i.atender(null, "Disco lleno", null, "Pendiente", false, AHORA);
            assertThat(i.getEstado()).isEqualTo(EstadoIncidencia.EN_ATENCION);
            i.atender(null, "Disco lleno", "Se amplió el volumen", "Resuelto", true, AHORA);
            assertThat(i.estaCerrada()).isTrue();
            assertThat(i.getFechaCierre()).isEqualTo(AHORA);
            assertThatThrownBy(() -> i.atender(null, "x", "x", "x", true, AHORA)).hasMessageContaining("cerrada");
        }
    }
}
