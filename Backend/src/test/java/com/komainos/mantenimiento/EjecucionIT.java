package com.komainos.mantenimiento;

import com.komainos.ejecucion.model.PropositoEjecucion;
import com.komainos.ejecucion.model.ResultadoEjecucion;
import com.komainos.ejecucion.model.SolicitudEjecucion;
import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.inventario.model.CalendarioSemanal;
import com.komainos.inventario.model.DiaSemana;
import com.komainos.inventario.model.GrupoMantenimiento;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.inventario.model.Servidor;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.service.ServicioRecuperacion;
import com.komainos.shared.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

/**
 * El orquestador con el adaptador simulado. El espía del transporte permite detener una tarea en curso o cambiar la
 * base mientras se ejecuta, para observar exclusión, secuencia y detenciones sin conexiones reales.
 */
@DisplayName("Orquestación de la ejecución (RF51, RF52, RF58-RF60, RF68)")
class EjecucionIT extends BaseAutomatizacionIT {

    @Autowired
    ServicioRecuperacion recuperacion;

    private static boolean esTarea(SolicitudEjecucion s) {
        return s != null && s.proposito() == PropositoEjecucion.TAREA;
    }

    private String estadosDeTareas(Integer idOrden) {
        return jdbc.queryForObject("select string_agg(t.estado::text, ',' order by d.posicion_ejecucion, t.orden_ejecucion) "
                + "from mop_tarea t join mop m using (id_mop) join orden_detalle d using (id_orden_detalle) where d.id_orden = ?",
                String.class, idOrden);
    }

    @Nested
    @DisplayName("Prueba piloto en grupos paralelos (RF58)")
    class Piloto {

        @Test
        @DisplayName("CP134: el piloto conforme habilita a los demás servidores")
        void conforme() {
            GrupoMantenimiento g = grupo("Grupo paralelo", ModoEjecucion.PARALELO, servidor, crear("srv-segundo", "10.90.0.2"));
            Integer id = lanzarGrupo(g);
            aprobarComoResponsable(id);

            assertThat(orquestador.drenar()).isEqualTo(2);

            assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_VALIDACION);
            assertThat(contar("select count(*) from orden_detalle where id_orden = ? and es_servidor_piloto", id)).isEqualTo(1);
        }

        @Test
        @DisplayName("CP135: un piloto fallido impide iniciar a los demás y abre una incidencia de prueba piloto")
        void fallido() {
            // Con igual cantidad de tareas, el piloto es el primero del grupo: srv-fallo
            GrupoMantenimiento g = grupo("Grupo con fallo", ModoEjecucion.PARALELO, crear("srv-fallo", "10.90.0.2"),
                    crear("srv-segundo", "10.90.0.3"));
            Integer id = lanzarGrupo(g);
            aprobarComoResponsable(id);

            orquestador.drenar();

            assertThat(estado(id)).isEqualTo(EstadoOrden.INCIDENCIA);
            assertThat(contar("select count(*) from orden_detalle where id_orden = ? and fecha_real_inicio is not null", id))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("select origen::text from incidencia", String.class)).isEqualTo("PRUEBA_PILOTO");
            assertThat(jdbc.queryForObject("select error from incidencia", String.class))
                    .contains("Estado de los servidores").contains("srv-segundo no iniciado");
        }
    }

    @Nested
    @DisplayName("Exclusión, secuencia y concurrencia")
    class Exclusion {

        @Test
        @DisplayName("CP129, CP130: dos órdenes sobre un servidor conservan una sola ejecución simultánea")
        void unaEjecucionPorServidor() throws Exception {
            Integer primera = lanzarAutorizada(servidor);
            Integer segunda = lanzarAutorizada(servidor);
            CountDownLatch iniciada = new CountDownLatch(1);
            CountDownLatch liberar = new CountDownLatch(1);
            doAnswer(inv -> {
                iniciada.countDown();
                if (!liberar.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Espera de prueba agotada");
                }
                return inv.callRealMethod();
            }).when(transporte).ejecutar(argThat(EjecucionIT::esTarea));
            try {
                assertThat(orquestador.despachar()).isEqualTo(1);
                assertThat(iniciada.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(orquestador.despachar()).isZero();
                assertThat(contar("select count(*) from orden_detalle where id_servidor = ? and estado = 'EN_EJECUCION'",
                        servidor.getId())).isEqualTo(1);
            } finally {
                liberar.countDown();
            }
            esperarEstado(primera, EstadoOrden.PENDIENTE_VALIDACION);
            orquestador.drenar();
            assertThat(estado(segunda)).isEqualTo(EstadoOrden.PENDIENTE_VALIDACION);
        }

        @Test
        @DisplayName("En un grupo secuencial, si el servidor que sigue está ocupado, el grupo espera su turno")
        void secuenciaRespetada() throws Exception {
            Servidor segundo = crear("srv-segundo", "10.90.0.2");
            Integer individual = lanzarAutorizada(servidor);
            Integer grupal = lanzarGrupo(grupo("Grupo secuencial", ModoEjecucion.SECUENCIAL, servidor, segundo));
            aprobarComoResponsable(grupal);
            CountDownLatch liberar = new CountDownLatch(1);
            AtomicBoolean primera = new AtomicBoolean(true);
            doAnswer(inv -> {
                if (primera.getAndSet(false) && !liberar.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Espera de prueba agotada");
                }
                return inv.callRealMethod();
            }).when(transporte).ejecutar(argThat(EjecucionIT::esTarea));
            try {
                assertThat(orquestador.despachar()).isEqualTo(1);
                assertThat(jdbc.queryForObject("select estado::text from orden_detalle where id_orden = ? and id_servidor = ?",
                        String.class, grupal, segundo.getId())).isEqualTo("EN_COLA");
            } finally {
                liberar.countDown();
            }
            esperarEstado(individual, EstadoOrden.PENDIENTE_VALIDACION);
            orquestador.drenar();
            assertThat(estado(grupal)).isEqualTo(EstadoOrden.PENDIENTE_VALIDACION);
        }

        @Test
        @DisplayName("RF68: el límite global de ejecuciones aplica a las órdenes no inmediatas")
        void limiteGlobal() throws Exception {
            Integer primera = lanzarAutorizada(servidor);
            Integer segunda = lanzarAutorizada(crear("srv-segundo", "10.90.0.2"));
            jdbc.update("update orden set origen = 'SOLICITUD_BAJO_DEMANDA' where id_orden in (?, ?)", primera, segunda);
            jdbc.update("update configuracion_sistema set max_ejecuciones_concurrentes = 1");
            CountDownLatch liberar = new CountDownLatch(1);
            doAnswer(inv -> {
                if (!liberar.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Espera de prueba agotada");
                }
                return inv.callRealMethod();
            }).when(transporte).ejecutar(argThat(EjecucionIT::esTarea));
            try {
                assertThat(orquestador.despachar()).isEqualTo(1);
                assertThat(orquestador.despachar()).isZero();
            } finally {
                liberar.countDown();
            }
            for (int i = 0; i < 300 && contar("select count(*) from orden_detalle where estado = 'EN_EJECUCION'") > 0; i++) {
                Thread.sleep(20);
            }
            orquestador.drenar();
            assertThat(estado(primera)).isEqualTo(EstadoOrden.PENDIENTE_VALIDACION);
            assertThat(estado(segunda)).isEqualTo(EstadoOrden.PENDIENTE_VALIDACION);
        }
    }

    @Nested
    @DisplayName("Condiciones de inicio y detención (RF52, RF60)")
    class Detencion {

        @Test
        @DisplayName("CP132: sin acceso al servidor no se inicia ninguna tarea y la prueba fallida es una incidencia")
        void sinAccesoAlIniciar() {
            Integer id = lanzarAutorizada(servidor);
            doReturn(ResultadoEjecucion.fallo(true, TipoFallo.COMUNICACION, "srv-prueba no respondió por SSH (simulado)"))
                    .when(transporte).probarConexion(any());

            orquestador.drenar();

            assertThat(estado(id)).isEqualTo(EstadoOrden.INCIDENCIA);
            assertThat(estadosDeTareas(id)).isEqualTo("NO_INICIADA,NO_INICIADA");
            assertThat(jdbc.queryForObject("select error from incidencia", String.class))
                    .startsWith("No se cumplieron las condiciones de inicio en srv-prueba").contains("Error de comunicación");
        }

        @Test
        @DisplayName("CP138: al terminar la ventana, la tarea en curso finaliza y las pendientes no se inician")
        void finDeVentana() {
            Integer id = lanzarAutorizada(servidor);
            AtomicBoolean primera = new AtomicBoolean(true);
            doAnswer(inv -> {
                if (primera.getAndSet(false)) {
                    jdbc.update("update orden set inicio_ventana_aplicada = ?, fin_ventana_aplicada = ? where id_orden = ?",
                            Timestamp.from(AHORA.minusSeconds(7200)), Timestamp.from(AHORA.minusSeconds(1)), id);
                }
                return inv.callRealMethod();
            }).when(transporte).ejecutar(argThat(EjecucionIT::esTarea));

            orquestador.drenar();

            assertThat(estado(id)).isEqualTo(EstadoOrden.INCIDENCIA);
            assertThat(estadosDeTareas(id)).isEqualTo("FINALIZADA,NO_INICIADA");
            assertThat(jdbc.queryForObject("select origen::text from incidencia", String.class)).isEqualTo("TIEMPO_EXCEDIDO");
            assertThat(contar("select count(*) from alerta where id_orden = ? and tipo = 'VENTANA_EXCEDIDA'", id)).isEqualTo(1);
        }

        @Test
        @DisplayName("CP139: superada la duración máxima por MOP, las pendientes no se inician")
        void duracionMaxima() {
            Integer id = lanzarAutorizada(servidor);
            AtomicBoolean primera = new AtomicBoolean(true);
            doAnswer(inv -> {
                if (primera.getAndSet(false)) {
                    jdbc.update("update orden_detalle set fecha_real_inicio = ? where id_orden = ?",
                            Timestamp.from(AHORA.minusSeconds(5 * 3600)), id);
                }
                return inv.callRealMethod();
            }).when(transporte).ejecutar(argThat(EjecucionIT::esTarea));

            orquestador.drenar();

            assertThat(estadosDeTareas(id)).isEqualTo("FINALIZADA,NO_INICIADA");
            assertThat(jdbc.queryForObject("select error from incidencia", String.class)).contains("duración máxima por MOP");
        }

        @Test
        @DisplayName("EA07: si la ventana terminó antes de iniciar, no se abre ninguna conexión")
        void ventanaTerminadaAntesDeIniciar() {
            Integer id = lanzarAutorizada(servidor);
            jdbc.update("update orden set inicio_ventana_aplicada = ?, fin_ventana_aplicada = ? where id_orden = ?",
                    Timestamp.from(AHORA.minusSeconds(7200)), Timestamp.from(AHORA), id);

            orquestador.drenar();

            assertThat(estado(id)).isEqualTo(EstadoOrden.INCIDENCIA);
            assertThat(estadosDeTareas(id)).isEqualTo("NO_INICIADA,NO_INICIADA");
            assertThat(jdbc.queryForObject("select error from incidencia", String.class)).startsWith("No se inició la intervención");
        }

        @Test
        @DisplayName("CP140: con la excepción confirmada fuera de ventana, la ejecución no se detiene por la ventana")
        void fueraDeVentanaConfirmada() {
            // Ventana solo los martes: AHORA es lunes
            Servidor martes = crear("srv-martes", "10.90.0.9", new CalendarioSemanal.IntervaloSemanal(DiaSemana.MARTES,
                    LocalTime.of(1, 0), DiaSemana.MARTES, LocalTime.of(23, 0)));

            assertThatThrownBy(() -> programacion.inmediata(martes.getId(), null, false, "Urgente", null, actor))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("confirme expresamente");
            Integer id = programacion.inmediata(martes.getId(), null, true, "Urgente fuera de ventana", null, actor).getId();
            evaluacion.evaluar(id, actor);
            aprobarComoResponsable(id);

            orquestador.drenar();

            assertThat(contar("select count(*) from confirmacion_fuera_ventana where id_orden = ?", id)).isEqualTo(1);
            assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_VALIDACION);
        }
    }

    @Test
    @DisplayName("Al reiniciar, lo que estaba en ejecución queda fallido con su incidencia y no se reintenta")
    void recuperacionAlReiniciar() {
        Integer id = lanzarAutorizada(servidor);
        jdbc.update("update orden set estado = 'EN_EJECUCION' where id_orden = ?", id);
        jdbc.update("update orden_detalle set estado = 'EN_EJECUCION', fecha_real_inicio = ? where id_orden = ?",
                Timestamp.from(AHORA), id);
        jdbc.update("update mop_tarea set estado = 'EN_EJECUCION', fecha_inicio = ? where orden_ejecucion = 1",
                Timestamp.from(AHORA));
        Integer version = jdbc.queryForObject("select id_script_version from evaluacion limit 1", Integer.class);
        jdbc.update("insert into evaluacion (id_servidor, id_script_version, id_cuenta_servicio, tipo, estado, fecha_inicio) "
                + "values (?, ?, ?, 'REVISION_PERIODICA', 'EN_CURSO', ?)", servidor.getId(), version, cuenta, Timestamp.from(AHORA));

        recuperacion.recuperar();

        assertThat(estado(id)).isEqualTo(EstadoOrden.INCIDENCIA);
        assertThat(estadosDeTareas(id)).isEqualTo("FALLIDA,NO_INICIADA");
        assertThat(jdbc.queryForObject("select error from incidencia", String.class)).contains("reinicio del sistema");
        assertThat(contar("select count(*) from evaluacion where estado = 'EN_CURSO'")).isZero();
        assertThat(orquestador.drenar()).isZero();
    }
}
