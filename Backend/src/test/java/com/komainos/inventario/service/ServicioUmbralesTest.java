package com.komainos.inventario.service;

import com.komainos.DatosPrueba;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.model.AlcanceExcepcion;
import com.komainos.inventario.model.Entorno;
import com.komainos.inventario.model.ListaReferencia;
import com.komainos.inventario.model.Metrica;
import com.komainos.inventario.model.OperadorComparacion;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.model.TipoDato;
import com.komainos.inventario.model.Umbral;
import com.komainos.inventario.model.Umbral.LimitesNumericos;
import com.komainos.inventario.repository.EntornoRepositorio;
import com.komainos.inventario.repository.GrupoMantenimientoRepositorio;
import com.komainos.inventario.repository.ListaReferenciaRepositorio;
import com.komainos.inventario.repository.MetricaRepositorio;
import com.komainos.inventario.repository.NivelCriticidadRepositorio;
import com.komainos.inventario.repository.ServidorRepositorio;
import com.komainos.inventario.repository.UmbralExcepcionRepositorio;
import com.komainos.inventario.repository.UmbralRepositorio;
import com.komainos.inventario.service.ServicioListasReferencia.DatosLista;
import com.komainos.inventario.service.ServicioMetricas.DatosMetrica;
import com.komainos.inventario.service.ServicioUmbrales.DatosExcepcion;
import com.komainos.inventario.service.ServicioUmbrales.DatosUmbral;
import com.komainos.seguridad.model.Rol;
import com.komainos.shared.exception.ConflictoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Reglas de los servicios de métricas, listas y umbrales (RF16, RNF06)")
class ServicioUmbralesTest {

    private static final Instant AHORA = Instant.parse("2026-10-01T15:00:00Z");
    private static final Actor ADMIN = Actor.usuario(1);

    private final Clock reloj = Clock.fixed(AHORA, ZoneOffset.UTC);
    private final ServicioAuditoria auditoria = mock(ServicioAuditoria.class);
    private final MetricaRepositorio metricas = mock(MetricaRepositorio.class);
    private final UmbralRepositorio umbrales = mock(UmbralRepositorio.class);
    private final UmbralExcepcionRepositorio excepciones = mock(UmbralExcepcionRepositorio.class);
    private final ListaReferenciaRepositorio listas = mock(ListaReferenciaRepositorio.class);
    private final ServidorRepositorio servidores = mock(ServidorRepositorio.class);
    private final GrupoMantenimientoRepositorio grupos = mock(GrupoMantenimientoRepositorio.class);
    private final EntornoRepositorio entornos = mock(EntornoRepositorio.class);
    private final NivelCriticidadRepositorio criticidades = mock(NivelCriticidadRepositorio.class);

    @Nested
    @DisplayName("métricas")
    class Metricas {

        private final ServicioMetricas servicio = new ServicioMetricas(metricas, umbrales, auditoria);

        @Test
        @DisplayName("CP186: un código repetido responde conflicto y se guarda en minúsculas")
        void codigoRepetido() {
            when(metricas.existsByCodigoIgnoreCase("cpu_uso_pct")).thenReturn(true);
            assertThatThrownBy(() -> servicio.crear(new DatosMetrica("CPU_uso_pct", "Uso de CPU", "%", TipoDato.DECIMAL,
                    false), ADMIN)).isInstanceOf(ConflictoException.class).hasMessageContaining("cpu_uso_pct");
        }

        @Test
        @DisplayName("CP198: el tipo de una métrica con umbrales no cambia, y el código nunca")
        void tipoConUmbrales() {
            Metrica cpu = DatosPrueba.metrica("cpu_uso_pct", TipoDato.DECIMAL, false);
            when(metricas.findById(5)).thenReturn(Optional.of(cpu));
            when(umbrales.countByMetricaId(5)).thenReturn(2L);

            assertThatThrownBy(() -> servicio.actualizar(5, new DatosMetrica(null, "Uso de CPU", "%", TipoDato.INTEGER,
                    false), ADMIN)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("2 umbral(es)");
            assertThatThrownBy(() -> servicio.actualizar(5, new DatosMetrica("otro", "Uso de CPU", "%", TipoDato.DECIMAL,
                    false), ADMIN)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no se modifica");
            // Nombre y unidad sí cambian
            servicio.actualizar(5, new DatosMetrica(null, "CPU", " pct ", TipoDato.DECIMAL, false), ADMIN);
            assertThat(cpu.getUnidad()).isEqualTo("pct");
            verify(auditoria).registrar(any(), any());
        }
    }

    @Nested
    @DisplayName("listas")
    class Listas {

        private final ServicioListasReferencia servicio =
                new ServicioListasReferencia(listas, umbrales, excepciones, auditoria, reloj);

        @Test
        @DisplayName("CP190: no se elimina la lista general de un umbral; el mensaje nombra el umbral")
        void eliminarEnUso() {
            ListaReferencia web = ListaReferencia.nueva("Puertos web", TipoDato.INTEGER, false, AHORA);
            when(listas.findById(3)).thenReturn(Optional.of(web));
            Umbral umbral = mock(Umbral.class);
            when(umbral.getNombre()).thenReturn("Puertos esperados");
            when(umbrales.findByListaIdOrderByNombreAsc(3)).thenReturn(List.of(umbral));

            assertThatThrownBy(() -> servicio.eliminar(3, ADMIN))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Puertos esperados");
            verify(listas, never()).delete(any());
        }

        @Test
        @DisplayName("el tipo de una lista no cambia")
        void tipoFijo() {
            when(listas.findById(3)).thenReturn(Optional.of(ListaReferencia.nueva("Puertos", TipoDato.INTEGER, false, AHORA)));
            assertThatThrownBy(() -> servicio.actualizar(3, new DatosLista("Puertos", TipoDato.STRING, false, List.of("a")),
                    ADMIN)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no se modifica");
        }
    }

    @Nested
    @DisplayName("umbrales y excepciones")
    class Umbrales {

        private final ServicioUmbrales servicio = new ServicioUmbrales(umbrales, excepciones, metricas, listas, servidores,
                grupos, entornos, criticidades, auditoria, reloj);
        private final Umbral cpu = Umbral.numerico(DatosPrueba.metrica("cpu_uso_pct", TipoDato.DECIMAL, false), "CPU alta",
                new LimitesNumericos(OperadorComparacion.MAYOR, new BigDecimal("80"), new BigDecimal("90")), AHORA);

        @Test
        @DisplayName("CP195: no se definen excepciones sobre un entorno inactivo ni un servidor dado de baja")
        void objetivosNoValidos() {
            when(umbrales.findConMetricaById(anyInt())).thenReturn(Optional.of(cpu));
            Entorno inactivo = DatosPrueba.entorno(2, "Contingencia");
            inactivo.desactivar();
            when(entornos.findById(2)).thenReturn(Optional.of(inactivo));
            Servidor deBaja = DatosPrueba.servidor(9, "SERVPRODLEGV01", DatosPrueba.usuario(5, "resp", Rol.RESPONSABLE));
            deBaja.aplicarBaja();
            when(servidores.findById(9)).thenReturn(Optional.of(deBaja));

            assertThatThrownBy(() -> servicio.crearExcepcion(1, new DatosExcepcion(AlcanceExcepcion.ENTORNO, 2,
                    new BigDecimal("70"), null, null, null), ADMIN))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Contingencia está inactivo");
            assertThatThrownBy(() -> servicio.crearExcepcion(1, new DatosExcepcion(AlcanceExcepcion.SERVIDOR, 9,
                    new BigDecimal("70"), null, null, null), ADMIN))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("dado de baja");
            assertThat(cpu.getExcepciones()).isEmpty();
        }

        @Test
        @DisplayName("un umbral numérico no acepta lista ni niveles de lista")
        void modalidadDeLaPeticion() {
            when(metricas.findById(5)).thenReturn(Optional.of(DatosPrueba.metrica("cpu_uso_pct", TipoDato.DECIMAL, false)));
            assertThatThrownBy(() -> servicio.crear(new DatosUmbral(5, "CPU", OperadorComparacion.MAYOR,
                    new BigDecimal("1"), null, 3, null, null), ADMIN))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no lleva lista");
        }

        @Test
        @DisplayName("un umbral ya evaluado no se elimina: se desactiva")
        void eliminarEvaluado() {
            when(umbrales.findConMetricaById(1)).thenReturn(Optional.of(cpu));
            when(umbrales.tieneEvaluaciones(1)).thenReturn(true);
            assertThatThrownBy(() -> servicio.eliminar(1, ADMIN))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("desactívelo");
            verify(umbrales, never()).delete(any());
        }
    }
}
