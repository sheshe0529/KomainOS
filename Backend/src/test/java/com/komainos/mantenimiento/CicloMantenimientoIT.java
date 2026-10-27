package com.komainos.mantenimiento;

import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.catalogo.model.Script;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion.FiltroScripts;
import com.komainos.inventario.model.CalendarioSemanal;
import com.komainos.inventario.model.DiaSemana;
import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.model.EstadoAutorizacion;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.service.ServicioIncidencias;
import com.komainos.mantenimiento.service.ServicioRevisionPeriodica;
import com.komainos.mantenimiento.service.ServicioAutorizaciones;
import com.komainos.mantenimiento.service.ServicioValidaciones;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.Rol;
import com.komainos.shared.exception.ReglaNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Ciclo de mantenimiento en modo simulado (RF37-RF65)")
class CicloMantenimientoIT extends BaseAutomatizacionIT {

    @Autowired
    ServicioValidaciones validaciones;
    @Autowired
    ServicioIncidencias incidencias;
    @Autowired
    ServicioRevisionPeriodica revision;
    @Autowired
    ServicioCatalogoAutomatizacion catalogo;

    private AlcanceUsuario comoResponsable() {
        return new AlcanceUsuario(responsable.getId(), Rol.RESPONSABLE);
    }

    private AlcanceUsuario comoAdministrador() {
        return new AlcanceUsuario(admin.getId(), Rol.ADMINISTRADOR);
    }

    @Test
    @DisplayName("RF37-RF57: evaluación, MOP, aprobación, ejecución, reporte comparativo y conformidad cierran el ciclo")
    void flujoCompleto() throws Exception {
        Integer id = lanzar(servidor);
        assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_AUTORIZACION);
        aprobarComoResponsable(id);
        assertThat(estado(id)).isEqualTo(EstadoOrden.AUTORIZADA);

        assertThat(orquestador.drenar()).isEqualTo(1);
        assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_VALIDACION);

        mvc.perform(get("/api/ordenes/{id}/automatizacion", id).with(como(responsable)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulado").value(true))
                .andExpect(jsonPath("$.acciones.validar").value(true))
                .andExpect(jsonPath("$.siguientePaso").value(org.hamcrest.Matchers.startsWith("Esperando la conformidad")))
                .andExpect(jsonPath("$.mops[0].afectacion").value("PARCIAL"))
                .andExpect(jsonPath("$.mops[0].tareas.length()").value(2))
                // RF41: por prioridad, la 10 antes que la 20
                .andExpect(jsonPath("$.mops[0].tareas[0].tarea").value("Liberar archivos temporales"))
                .andExpect(jsonPath("$.mops[0].tareas[0].estado").value("FINALIZADA"))
                .andExpect(jsonPath("$.mops[0].tareas[0].salida").value(org.hamcrest.Matchers.containsString("SOLO_SIMULAR=true")))
                .andExpect(jsonPath("$.mops[0].tareas[0].cuenta").value("Cuenta de pruebas"))
                .andExpect(jsonPath("$.evaluaciones.length()").value(2))
                .andExpect(jsonPath("$.reporte[0].metricas[?(@.codigo == 'cpu_uso_pct')].nivelAntes").value("ADVERTENCIA"))
                .andExpect(jsonPath("$.reporte[0].metricas[?(@.codigo == 'cpu_uso_pct')].nivelDespues").value("NORMAL"))
                .andExpect(jsonPath("$.reporte[0].tareasFinalizadas").value(2))
                .andExpect(jsonPath("$.validacionesTecnicas[0].resultado").value("CONFORME"))
                .andExpect(jsonPath("$..secreto").doesNotExist())
                .andExpect(jsonPath("$..ansible_password").doesNotExist());

        mvc.perform(post("/api/ordenes/{id}/validacion", id).with(como(responsable))
                        .contentType("application/json").content("{\"conforme\":true,\"observaciones\":\"Servicio operativo\"}"))
                .andExpect(status().isNoContent());
        assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);
        assertThat(contar("select count(*) from cierre_orden where id_orden = ?", id)).isEqualTo(1);
    }

    @Test
    @DisplayName("EA02: si la evaluación previa no activa tareas, el ciclo se cierra sin intervención")
    void sinIntervencion() {
        Integer id = lanzar(crear("srv-saludable", "10.90.0.2"));

        assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);
        assertThat(contar("select count(*) from mop m join orden_detalle d using (id_orden_detalle) where d.id_orden = ?", id)).isZero();
        assertThat(contar("select count(*) from cierre_orden where id_orden = ?", id)).isEqualTo(1);
    }

    @Nested
    @DisplayName("Evaluación previa y MOP")
    class Evaluacion {

        @Test
        @DisplayName("HU24 CA5: sin la política completa del entorno la orden no se evalúa y queda programada")
        void politicaIncompleta() {
            jdbc.update("delete from notificacion_regla where id_regla_politica_cambio in (select id_regla_politica_cambio "
                    + "from regla_politica_cambio where nivel_afectacion = 'TOTAL')");
            jdbc.update("delete from regla_politica_cambio where nivel_afectacion = 'TOTAL'");
            Integer id = programacion.inmediata(servidor.getId(), null, false, "Prueba", null, actor).getId();

            assertThatThrownBy(() -> evaluacion.evaluar(id, actor))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("faltan las reglas de TOTAL");
            assertThat(estado(id)).isEqualTo(EstadoOrden.PROGRAMADA);
        }

        @Test
        @DisplayName("CP112: sin implementación compatible se bloquea, se conservan las tareas y se alerta al administrador")
        void sinImplementacion() throws Exception {
            Script limpieza = catalogo.scripts(FiltroScripts.todos(), PageRequest.of(0, 100)).stream()
                    .filter(s -> s.getNombre().equals("Liberar archivos temporales")).findFirst().orElseThrow();
            catalogo.habilitar(limpieza.getImplementaciones().getFirst().getVersiones().getFirst().getId(), false, actor);

            Integer id = lanzar(servidor);

            assertThat(estado(id)).isEqualTo(EstadoOrden.SIN_IMPLEMENTACION);
            assertThat(contar("select count(*) from mop_tarea t join mop m using (id_mop) join orden_detalle d "
                    + "using (id_orden_detalle) where d.id_orden = ?", id)).isEqualTo(2);
            assertThat(contar("select count(*) from alerta a join alerta_destinatario ad using (id_alerta) "
                    + "where a.id_orden = ? and a.tipo = 'SIN_IMPLEMENTACION' and ad.id_usuario = ?", id, admin.getId())).isEqualTo(1);
            assertThat(orquestador.drenar()).isZero();

            mvc.perform(post("/api/ordenes/{id}/cierre-sin-implementacion", id).with(como(admin))
                            .contentType("application/json").content("{\"motivo\":\"Se publicará la implementación\"}"))
                    .andExpect(status().isNoContent());
            assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);
        }

        @Test
        @DisplayName("RF24, RF42: un servidor sin script de análisis para su versión bloquea la orden con el motivo")
        void sinAnalisisCompatible() {
            Integer ubuntu22 = catalogos.agregarVersion(catalogos.obtenerVersion(versionUbuntu).getSistemaOperativo().getId(),
                    "22.04", actor).getVersiones().stream().filter(v -> v.getVersion().equals("22.04")).findFirst()
                    .orElseThrow().getId();
            Servidor antiguo = crear("srv-antiguo", "10.90.0.9", ubuntu22, TODA_LA_SEMANA);

            Integer id = lanzar(antiguo);

            assertThat(estado(id)).isEqualTo(EstadoOrden.SIN_IMPLEMENTACION);
            assertThat(jdbc.queryForObject("select motivo from historial_estado_orden where id_orden = ? "
                    + "and estado_nuevo = 'SIN_IMPLEMENTACION'", String.class, id))
                    .contains("srv-antiguo").contains("Ubuntu 22.04");
        }

        @Test
        @DisplayName("Un análisis previo sin conexión no se interpreta como «sin intervención»: bloquea con el motivo")
        void analisisPrevioFallido() {
            Integer id = lanzar(crear("srv-sin-conexion", "10.90.0.3"));

            assertThat(estado(id)).isEqualTo(EstadoOrden.SIN_IMPLEMENTACION);
            assertThat(jdbc.queryForObject("select motivo from historial_estado_orden where id_orden = ? "
                    + "and estado_nuevo = 'SIN_IMPLEMENTACION'", String.class, id))
                    .contains("srv-sin-conexion").contains("Error de comunicación");
        }

        @Test
        @DisplayName("CP081: una versión nueva no cambia la fijada en una MOP pendiente; la siguiente MOP fija la nueva")
        void versionesInmutables() {
            Integer primera = lanzar(servidor);
            Integer fijada = jdbc.queryForObject("select t.id_script_version from mop_tarea t join mop m using (id_mop) "
                    + "join orden_detalle d using (id_orden_detalle) where d.id_orden = ? and t.orden_ejecucion = 1",
                    Integer.class, primera);
            Integer implementacion = jdbc.queryForObject("select id_script_implementacion from script_version "
                    + "where id_script_version = ?", Integer.class, fijada);
            catalogo.publicar(implementacion, new ServicioCatalogoAutomatizacion.DatosVersion("#!/bin/bash\necho nueva\n",
                    "Versión posterior", java.util.List.of()), actor);

            Integer segunda = lanzar(crear("srv-segundo", "10.90.0.4"));

            assertThat(jdbc.queryForObject("select t.id_script_version from mop_tarea t join mop m using (id_mop) "
                    + "join orden_detalle d using (id_orden_detalle) where d.id_orden = ? and t.orden_ejecucion = 1",
                    Integer.class, primera)).isEqualTo(fijada);
            assertThat(jdbc.queryForObject("select v.numero_version from mop_tarea t join mop m using (id_mop) "
                    + "join orden_detalle d using (id_orden_detalle) join script_version v using (id_script_version) "
                    + "where d.id_orden = ? and t.orden_ejecucion = 1", Integer.class, segunda)).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Autorización (RF44-RF50, RF75)")
    class Autorizacion {

        @Test
        @DisplayName("CP113, CP114, CP126: responsable primero, administrador después y un plazo compartido")
        void secuencia() {
            exigir(NivelAfectacion.PARCIAL, true, true);
            Integer id = lanzar(servidor);
            Timestamp limite = jdbc.queryForObject("select fecha_limite from solicitud_autorizacion where id_orden = ?",
                    Timestamp.class, id);

            assertThatThrownBy(() -> autorizaciones.decidir(id, EstadoAutorizacion.APROBADA, "Anticipada", null,
                    comoAdministrador())).hasMessageContaining("espera la decisión del responsable");
            aprobarComoResponsable(id);
            assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_AUTORIZACION);
            autorizaciones.decidir(id, EstadoAutorizacion.APROBADA, "Aprobado", null, comoAdministrador());

            assertThat(estado(id)).isEqualTo(EstadoOrden.AUTORIZADA);
            // CP127: en un lanzamiento inmediato el plazo corre desde la primera solicitud con las horas de la criticidad
            assertThat(limite.toInstant()).isEqualTo(AHORA.plusSeconds(24 * 3600));
            assertThat(jdbc.queryForObject("select fecha_limite from solicitud_autorizacion where id_orden = ?",
                    Timestamp.class, id)).isEqualTo(limite);
        }

        @Test
        @DisplayName("CP116: sin aprobadores la orden queda autorizada y se envían las notificaciones de la regla")
        void automatica() {
            exigir(NivelAfectacion.PARCIAL, false, false);
            jdbc.update("insert into notificacion_regla (id_regla_politica_cambio, destinatario) select "
                    + "id_regla_politica_cambio, 'RESPONSABLE' from regla_politica_cambio where nivel_afectacion = 'PARCIAL'");

            Integer id = lanzar(servidor);

            assertThat(estado(id)).isEqualTo(EstadoOrden.AUTORIZADA);
            assertThat(contar("select count(*) from autorizacion_detalle a join solicitud_autorizacion s "
                    + "using (id_solicitud_autorizacion) where s.id_orden = ?", id)).isZero();
            assertThat(contar("select count(*) from alerta a join alerta_destinatario d using (id_alerta) where a.id_orden = ? "
                    + "and a.titulo like 'Política de cambio aplicada%' and d.id_usuario = ?", id, responsable.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("CP119, CP152: el rechazo exige motivo, cierra el ciclo y no abre una incidencia")
        void rechazo() throws Exception {
            Integer id = lanzar(servidor);

            mvc.perform(post("/api/ordenes/{id}/autorizacion", id).with(como(responsable)).contentType("application/json")
                            .content("{\"decision\":\"RECHAZADA\",\"motivo\":\" \"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(post("/api/ordenes/{id}/autorizacion", id).with(como(responsable)).contentType("application/json")
                            .content("{\"decision\":\"RECHAZADA\",\"motivo\":\"Cierre contable en curso\"}"))
                    .andExpect(status().isNoContent());

            assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);
            assertThat(contar("select count(*) from historial_estado_orden where id_orden = ? and estado_nuevo = 'RECHAZADA'", id))
                    .isEqualTo(1);
            assertThat(contar("select count(*) from incidencia i join orden_detalle d using (id_orden_detalle) where d.id_orden = ?", id))
                    .isZero();
        }

        @Test
        @DisplayName("CP121, CP122: la fecha propuesta se verifica al proponerla y al aceptarla abre otra ronda")
        void propuestaDeFecha() {
            // Ventana de lunes 06:00 a 14:00 (Lima): AHORA es lunes 09:00
            Servidor acotado = crear("srv-acotado", "10.90.0.5", new CalendarioSemanal.IntervaloSemanal(DiaSemana.LUNES,
                    LocalTime.of(6, 0), DiaSemana.LUNES, LocalTime.of(14, 0)));
            Integer id = lanzar(acotado);
            Instant fueraDeVentana = Instant.parse("2026-10-05T20:00:00Z");
            Instant dentroDeVentana = Instant.parse("2026-10-05T12:00:00Z");

            assertThatThrownBy(() -> autorizaciones.decidir(id, EstadoAutorizacion.FECHA_PROPUESTA, "Por la tarde",
                    fueraDeVentana, comoResponsable())).isInstanceOf(ReglaNegocioException.class);
            assertThat(jdbc.queryForObject("select estado::text from autorizacion_detalle", String.class)).isEqualTo("PENDIENTE");

            autorizaciones.decidir(id, EstadoAutorizacion.FECHA_PROPUESTA, "Lunes siguiente temprano", dentroDeVentana,
                    comoResponsable());
            assertThatThrownBy(() -> autorizaciones.decidir(id, EstadoAutorizacion.APROBADA, "Aprobar", null,
                    comoAdministrador())).hasMessageContaining("aceptarla o rechazar");
            autorizaciones.aceptarPropuesta(id, actor);

            assertThat(jdbc.queryForObject("select fecha_inicio_programada from orden where id_orden = ?", Timestamp.class, id)
                    .toInstant()).isEqualTo(dentroDeVentana);
            assertThat(contar("select count(*) from solicitud_autorizacion where id_orden = ?", id)).isEqualTo(2);
            assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_AUTORIZACION);
            assertThat(orquestador.drenar()).isZero();
        }

        @Test
        @DisplayName("CP124: vencido el plazo se registra la aprobación vencida, se alerta y se cierra sin ejecutar")
        void vencimiento() {
            Integer id = lanzar(servidor);
            jdbc.update("update solicitud_autorizacion set fecha_limite = ? where id_orden = ?",
                    Timestamp.from(AHORA.minusSeconds(1)), id);

            assertThat(autorizaciones.vencer()).isEqualTo(1);

            assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);
            assertThat(jdbc.queryForObject("select estado::text from autorizacion_detalle", String.class)).isEqualTo("VENCIDA");
            assertThat(contar("select count(*) from alerta where id_orden = ? and tipo = 'AUTORIZACION_VENCIDA'", id)).isEqualTo(1);
            assertThat(orquestador.drenar()).isZero();
        }
    }

    @Nested
    @DisplayName("Validación e incidencias (RF54-RF62)")
    class Validacion {

        @Test
        @DisplayName("CP151, RF59: una tarea fallida detiene las pendientes y abre una incidencia")
        void falloDeTarea() {
            Integer id = lanzarAutorizada(crear("srv-fallo", "10.90.0.2"));
            orquestador.drenar();

            assertThat(estado(id)).isEqualTo(EstadoOrden.INCIDENCIA);
            assertThat(jdbc.queryForObject("select origen::text from incidencia", String.class)).isEqualTo("FALLO_EJECUCION");
            assertThat(jdbc.queryForObject("select string_agg(t.estado::text, ',' order by t.orden_ejecucion) from mop_tarea t",
                    String.class)).isEqualTo("FALLIDA,NO_INICIADA");
        }

        @Test
        @DisplayName("CP144: si las métricas posteriores siguen fuera de umbral, la validación técnica abre una incidencia")
        void noConforme() {
            Integer id = lanzarAutorizada(crear("srv-no-conforme", "10.90.0.3"));
            orquestador.drenar();

            assertThat(estado(id)).isEqualTo(EstadoOrden.INCIDENCIA);
            assertThat(jdbc.queryForObject("select observaciones from validacion_tecnica", String.class))
                    .startsWith("Persisten umbrales activados");
            assertThat(jdbc.queryForObject("select origen::text from incidencia", String.class))
                    .isEqualTo("VALIDACION_TECNICA_NO_CONFORME");
        }

        @Test
        @DisplayName("CP145: la validación funcional se solicita al responsable con el plazo de la criticidad")
        void solicitudDeValidacion() {
            Integer id = lanzarAutorizada(servidor);
            orquestador.drenar();

            assertThat(jdbc.queryForObject("select fecha_limite from validacion_funcional where id_orden = ?", Timestamp.class, id)
                    .toInstant()).isEqualTo(AHORA.plusSeconds(48 * 3600));
            assertThat(contar("select count(*) from alerta a join alerta_destinatario d using (id_alerta) where a.id_orden = ? "
                    + "and a.tipo = 'SOLICITUD_VALIDACION' and d.id_usuario = ?", id, responsable.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("CP147: el rechazo funcional conserva la respuesta y abre una incidencia")
        void rechazoFuncional() {
            Integer id = lanzarAutorizada(servidor);
            orquestador.drenar();

            validaciones.validar(id, false, "El servicio sigue lento", comoResponsable());

            assertThat(estado(id)).isEqualTo(EstadoOrden.INCIDENCIA);
            assertThat(jdbc.queryForObject("select estado::text from validacion_funcional", String.class)).isEqualTo("RECHAZADA");
            assertThat(jdbc.queryForObject("select origen::text from incidencia", String.class)).isEqualTo("RECHAZO_FUNCIONAL");
        }

        @Test
        @DisplayName("CP148: una validación vencida cierra el ciclo sin registrar conformidad")
        void vencimientoDeValidacion() {
            Integer id = lanzarAutorizada(servidor);
            orquestador.drenar();
            jdbc.update("update validacion_funcional set fecha_solicitud = ?, fecha_limite = ? where id_orden = ?",
                    Timestamp.from(AHORA.minusSeconds(7200)), Timestamp.from(AHORA.minusSeconds(3600)), id);

            assertThat(validaciones.vencer()).isEqualTo(1);

            assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);
            assertThat(jdbc.queryForObject("select estado::text from validacion_funcional", String.class)).isEqualTo("VENCIDA");
            assertThat(contar("select count(*) from historial_estado_orden where id_orden = ? and estado_nuevo = 'VALIDACION_VENCIDA'", id))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("CP149, CP150: cerrar una incidencia exige diagnóstico y resultado; cerrada, la orden termina sin reanudar")
        void atencionDeIncidencia() throws Exception {
            Integer id = lanzarAutorizada(crear("srv-fallo", "10.90.0.2"));
            orquestador.drenar();
            Integer incidencia = jdbc.queryForObject("select id_incidencia from incidencia", Integer.class);

            mvc.perform(put("/api/incidencias/{id}", incidencia).with(como(admin)).contentType("application/json")
                            .content("{\"diagnostico\":\"\",\"acciones\":\"\",\"resultado\":\"\",\"cerrar\":true}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/api/incidencias/{id}", incidencia).with(como(admin)).contentType("application/json")
                            .content("{\"diagnostico\":\"Carpeta inexistente\",\"acciones\":\"Se creó la carpeta\","
                                    + "\"resultado\":\"Resuelto\",\"cerrar\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CERRADA"))
                    .andExpect(jsonPath("$.atendidaPor").value("Administrador de pruebas"));

            assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);
            assertThat(contar("select count(*) from mop_tarea where estado = 'NO_INICIADA'")).isEqualTo(1);
            assertThat(orquestador.drenar()).isZero();
        }
    }

    @Nested
    @DisplayName("Revisión periódica (RF37, RF40)")
    class Revision {

        @Test
        @DisplayName("CP102: una advertencia alerta al responsable y al administrador sin generar una orden")
        void advertencia() {
            Servidor s = crear("srv-advertencia", "10.90.0.6");

            revision.revisar(s.getId(), actor);

            assertThat(contar("select count(*) from alerta a join alerta_destinatario d using (id_alerta) "
                    + "where a.id_servidor = ? and a.tipo = 'ADVERTENCIA_UMBRAL' and d.id_usuario in (?, ?)",
                    s.getId(), responsable.getId(), admin.getId())).isEqualTo(2);
            assertThat(contar("select count(*) from orden where id_servidor = ?", s.getId())).isZero();
        }

        @Test
        @DisplayName("CP103: una revisión sin conexión no es concluyente y genera una alerta")
        void noConcluyente() {
            Servidor s = crear("srv-sin-conexion", "10.90.0.7");

            var e = revision.revisar(s.getId(), actor);

            assertThat(e.getEstado().name()).isEqualTo("FALLIDA");
            assertThat(e.getError()).startsWith("Error de comunicación");
            assertThat(contar("select count(*) from alerta where id_servidor = ? and titulo like 'Revisión no concluyente%'",
                    s.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("CP107, CP128: la condición crítica genera una orden aunque haya un ciclo programado, con la política")
        void condicionCritica() {
            Servidor s = crear("srv-critico", "10.90.0.8");
            servidores.configurar(s.getId(), new ServicioServidor.DatosConfiguracion(7, 30,
                    ModalidadPlanificacion.AUTOMATICA, cuenta), actor);
            assertThat(contar("select count(*) from orden where id_servidor = ? and estado = 'PROGRAMADA'", s.getId())).isEqualTo(1);

            revision.revisar(s.getId(), actor);

            Integer critica = jdbc.queryForObject("select id_orden from orden where id_servidor = ? and origen = 'CONDICION_CRITICA' "
                    + "and id_evaluacion_origen is not null", Integer.class, s.getId());
            assertThat(estado(critica)).isEqualTo(EstadoOrden.PENDIENTE_AUTORIZACION);
            assertThat(jdbc.queryForObject("select fecha_limite from solicitud_autorizacion where id_orden = ?", Timestamp.class,
                    critica).toInstant()).isEqualTo(AHORA.plusSeconds(24 * 3600));

            revision.revisar(s.getId(), actor);
            assertThat(contar("select count(*) from orden where id_servidor = ? and origen = 'CONDICION_CRITICA'", s.getId()))
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("RF29, RF64: el cierre de un ciclo automático planifica el siguiente")
    void siguienteCiclo() {
        Integer id = lanzarAutorizada(servidor);
        orquestador.drenar();
        servidores.configurar(servidor.getId(), new ServicioServidor.DatosConfiguracion(7, 30,
                ModalidadPlanificacion.AUTOMATICA, cuenta), actor);

        validaciones.validar(id, true, "Conforme", comoResponsable());

        assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);
        assertThat(contar("select count(*) from cierre_orden where id_orden = ? and id_orden_siguiente is not null", id))
                .isEqualTo(1);
        assertThat(contar("select count(*) from orden where id_servidor = ? and origen = 'PLANIFICACION_AUTOMATICA'",
                servidor.getId())).isEqualTo(1);
    }
}
