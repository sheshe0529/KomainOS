package com.komainos.mantenimiento;

import com.komainos.alerta.model.TipoAlerta;
import com.komainos.alerta.service.ServicioAlertas;
import com.komainos.alerta.service.ServicioAlertas.DatosAlerta;
import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.GrupoMantenimiento;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.ReglaPoliticaCambio;
import com.komainos.mantenimiento.service.ServicioIncidencias;
import com.komainos.mantenimiento.service.ServicioIncidencias.DatosAtencion;
import com.komainos.mantenimiento.service.ServicioValidaciones;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.shared.model.Actor;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.sql.Timestamp;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("API de operación de la automatización por HTTP")
class OperacionAutomatizacionIT extends BaseAutomatizacionIT {

    @Autowired
    ServicioAlertas alertas;
    @Autowired
    ServicioIncidencias incidencias;
    @Autowired
    ServicioValidaciones validaciones;

    @Nested
    @DisplayName("Permisos por rol")
    class Permisos {

        @Test
        @DisplayName("El operador consulta pero no ejecuta; el responsable no ve la sala ni órdenes ajenas (404)")
        void alcance() throws Exception {
            Integer id = lanzar(servidor);
            Usuario ajeno = usuarios.crear("ajeno.auto", "Otro responsable", "Clave-prueba-4", Rol.RESPONSABLE, actor);

            mvc.perform(post("/api/automatizacion/procesar").with(como(operador))).andExpect(status().isForbidden());
            mvc.perform(post("/api/servidores/{id}/conectividad", servidor.getId()).with(como(operador)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/automatizacion/sala").with(como(operador))).andExpect(status().isOk());
            mvc.perform(get("/api/automatizacion/sala").with(como(responsable))).andExpect(status().isForbidden());
            mvc.perform(get("/api/ordenes/{id}/automatizacion", id).with(como(ajeno))).andExpect(status().isNotFound());
            // El responsable del servidor sí ve su orden y puede aprobarla en su turno
            mvc.perform(get("/api/ordenes/{id}/automatizacion", id).with(como(responsable)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.acciones.decidir").value(true))
                    .andExpect(jsonPath("$.acciones.proponerFecha").value(true))
                    .andExpect(jsonPath("$.acciones.evaluar").value(false));
        }
    }

    @Nested
    @DisplayName("Lanzamiento y evaluación desde el panel")
    class Panel {

        @Test
        @DisplayName("RF31: el lanzamiento valida antes de crear la orden y la evaluación continúa en segundo plano")
        void lanzamientoInmediato() throws Exception {
            jdbc.update("update script set activo = false where tipo = 'ANALISIS'");
            mvc.perform(post("/api/automatizacion/inmediata").with(como(admin)).contentType("application/json")
                            .content("{\"idServidor\":%d,\"motivo\":\"Disco lleno\"}".formatted(servidor.getId())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("script de análisis")));
            assertThat(contar("select count(*) from orden")).isZero();

            jdbc.update("update script set activo = true where tipo = 'ANALISIS'");
            String cuerpo = mvc.perform(post("/api/automatizacion/inmediata").with(como(admin)).contentType("application/json")
                            .content("{\"idServidor\":%d,\"motivo\":\"Disco lleno\"}".formatted(servidor.getId())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nombre").value(Matchers.startsWith("OM-")))
                    .andReturn().getResponse().getContentAsString();
            Integer id = Integer.valueOf(cuerpo.replaceAll(".*\"id\":(\\d+).*", "$1"));

            esperarEstado(id, EstadoOrden.PENDIENTE_AUTORIZACION);
            assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_AUTORIZACION);
        }

        @Test
        @DisplayName("RF38: la evaluación de una orden programada responde 202 y avanza sola")
        void evaluacionAsincrona() throws Exception {
            Integer id = programacion.inmediata(servidor.getId(), null, false, "Prueba", null, actor).getId();

            mvc.perform(post("/api/ordenes/{id}/evaluacion", id).with(como(admin)))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("Evaluación previa iniciada")));
            esperarEstado(id, EstadoOrden.PENDIENTE_AUTORIZACION);

            assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_AUTORIZACION);
            mvc.perform(post("/api/ordenes/{id}/evaluacion", id).with(como(admin)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("Procesar ciclo devuelve un resumen legible de lo que hizo")
        void procesar() throws Exception {
            Integer id = lanzarAutorizada(servidor);

            mvc.perform(post("/api/automatizacion/procesar").with(como(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ejecutado").value(true))
                    .andExpect(jsonPath("$.despachadas").value(1))
                    .andExpect(jsonPath("$.revisadas").value(1))
                    .andExpect(jsonPath("$.mensajes.length()").value(4));
            // La cola corre en segundo plano: se espera antes de limpiar la base
            esperarEstado(id, EstadoOrden.PENDIENTE_VALIDACION);
            assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_VALIDACION);
        }
    }

    @Nested
    @DisplayName("Conectividad y preparación (RF71)")
    class Conectividad {

        @Test
        @DisplayName("CP020-CP023: el resultado distingue comunicación, autenticación y permisos")
        void categorias() throws Exception {
            mvc.perform(post("/api/servidores/{id}/conectividad", servidor.getId()).with(como(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.simulado").value(true))
                    .andExpect(jsonPath("$.correcto").value(true))
                    .andExpect(jsonPath("$.cuenta").value("Cuenta de pruebas"));
            mvc.perform(post("/api/servidores/{id}/conectividad", crear("srv-sin-autenticacion", "10.90.0.2").getId())
                            .with(como(admin)))
                    .andExpect(jsonPath("$.correcto").value(false))
                    .andExpect(jsonPath("$.fallo").value("AUTENTICACION"));
            mvc.perform(post("/api/servidores/{id}/conectividad", crear("srv-sin-permisos", "10.90.0.3").getId())
                            .with(como(admin)))
                    .andExpect(jsonPath("$.fallo").value("PERMISOS"));
            assertThat(contar("select count(*) from auditoria where operacion = 'PROBAR_CONECTIVIDAD'")).isEqualTo(3);
        }

        @Test
        @DisplayName("CP021: con una cuenta candidata se prueba cada integrante del grupo y se identifica el que falla")
        void grupoConCuentaCandidata() throws Exception {
            GrupoMantenimiento g = grupo("Grupo de prueba", ModoEjecucion.PARALELO, servidor,
                    crear("srv-sin-conexion", "10.90.0.4"));

            mvc.perform(post("/api/grupos/{id}/conectividad", g.getId()).param("idCuenta", cuenta.toString()).with(como(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[?(@.servidor == 'srv-sin-conexion')].fallo").value("COMUNICACION"))
                    .andExpect(jsonPath("$[?(@.servidor == 'srv-prueba')].correcto").value(true));
        }

        @Test
        @DisplayName("La preparación indica lo que falta para que el ciclo funcione en el servidor")
        void preparacion() throws Exception {
            mvc.perform(get("/api/servidores/{id}/preparacion-automatizacion", servidor.getId()).with(como(operador)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.listo").value(true))
                    .andExpect(jsonPath("$.comprobaciones[?(@.codigo == 'ANALISIS')].estado").value("OK"))
                    .andExpect(jsonPath("$.comprobaciones[?(@.codigo == 'TRANSPORTE')].estado").value("INFO"));

            jdbc.update("update regla_politica_cambio set activo = false where nivel_afectacion = 'TOTAL'");
            mvc.perform(get("/api/servidores/{id}/preparacion-automatizacion", servidor.getId()).with(como(admin)))
                    .andExpect(jsonPath("$.listo").value(false))
                    .andExpect(jsonPath("$.comprobaciones[?(@.codigo == 'POLITICA')].estado").value("ERROR"));
        }

        @Test
        @DisplayName("El diagnóstico del transporte informa el modo y no expone secretos")
        void diagnostico() throws Exception {
            mvc.perform(get("/api/automatizacion/modo").with(como(responsable)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.modo").value("SIMULADO"))
                    .andExpect(jsonPath("$.simulado").value(true))
                    .andExpect(jsonPath("$.transporte.puertoWinrm").value(5986))
                    .andExpect(jsonPath("$.avisos[0]").value(Matchers.startsWith("Modo simulado")));
        }
    }

    @Nested
    @DisplayName("Catálogo de scripts (RF24-RF26)")
    class Catalogo {

        @Test
        @DisplayName("CP079, CP080: la implementación nace con su primera versión y sus parámetros")
        void implementacionConVersion() throws Exception {
            var windows = catalogos.crearSistemaOperativo("Windows Server", FamiliaSistemaOperativo.WINDOWS, actor);
            Integer version2022 = catalogos.agregarVersion(windows.getId(), "2022", actor).getVersiones().getFirst().getId();
            Integer idScript = jdbc.queryForObject("select id_script from script where nombre = 'Revisar consumo de memoria'",
                    Integer.class);
            String cuerpo = """
                    {"idVersionSistemaOperativo":%d,"contenido":"Get-Process | Select-Object -First $TOP","notasCambio":"Primera",
                     "parametros":[{"nombre":"TOP","tipo":"INTEGER","obligatorio":true,"valorPorDefecto":"5"}]}""".formatted(version2022);

            mvc.perform(post("/api/scripts/{id}/implementaciones", idScript).with(como(admin))
                            .contentType("application/json").content(cuerpo))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.implementaciones[?(@.sistemaOperativo == 'Windows Server 2022')].lenguaje").value("powershell"))
                    .andExpect(jsonPath("$.implementaciones[?(@.sistemaOperativo == 'Windows Server 2022')].versionVigente").value(1));
            mvc.perform(post("/api/scripts/{id}/implementaciones", idScript).with(como(admin))
                            .contentType("application/json").content(cuerpo))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("Un parámetro obligatorio sin valor y un param() en PowerShell se rechazan con el motivo")
        void reglasDeParametros() throws Exception {
            Integer implementacion = jdbc.queryForObject("select i.id_script_implementacion from script_implementacion i "
                    + "join script s using (id_script) where s.nombre = 'Liberar archivos temporales'", Integer.class);

            mvc.perform(post("/api/scripts/implementaciones/{id}/versiones", implementacion).with(como(admin))
                            .contentType("application/json")
                            .content("{\"contenido\":\"echo $DIAS\",\"parametros\":[{\"nombre\":\"DIAS\",\"tipo\":\"INTEGER\",\"obligatorio\":true}]}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("necesita un valor por defecto")));
            mvc.perform(post("/api/scripts/vista-previa").with(como(operador)).contentType("application/json")
                            .content("{\"familia\":\"WINDOWS\",\"contenido\":\"param($X)\\nWrite-Output $X\",\"parametros\":"
                                    + "[{\"nombre\":\"X\",\"tipo\":\"STRING\",\"obligatorio\":false,\"valorPorDefecto\":\"a\"}]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.problemas[0]").value(Matchers.containsString("param()")))
                    .andExpect(jsonPath("$.contenido").value(Matchers.containsString("$X = 'a'")));
        }

        @Test
        @DisplayName("CP076: no admite un segundo script de análisis activo para la misma versión de sistema operativo")
        void analisisUnico() throws Exception {
            String nuevo = mvc.perform(post("/api/scripts").with(como(admin)).contentType("application/json")
                            .content("{\"nombre\":\"Otro análisis\",\"tipo\":\"ANALISIS\",\"activo\":true}"))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            Integer id = Integer.valueOf(nuevo.replaceAll("^\\{\"id\":(\\d+).*", "$1"));

            mvc.perform(post("/api/scripts/{id}/implementaciones", id).with(como(admin)).contentType("application/json")
                            .content("{\"idVersionSistemaOperativo\":%d,\"contenido\":\"echo {}\",\"parametros\":[]}"
                                    .formatted(versionUbuntu)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("Análisis básico de salud")));
        }

        @Test
        @DisplayName("Revisar una salida informa las métricas reconocidas, las desconocidas y las que faltan")
        void revisarSalida() throws Exception {
            mvc.perform(post("/api/scripts/revisar-salida").with(como(operador)).contentType("application/json")
                            .content("{\"salida\":\"Inicio\\n{\\\"metricas\\\":[{\\\"codigo\\\":\\\"cpu_uso_pct\\\",\\\"valor\\\":12},"
                                    + "{\\\"codigo\\\":\\\"temperatura\\\",\\\"valor\\\":40}]}\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.valida").value(false))
                    .andExpect(jsonPath("$.metricas[?(@.codigo == 'cpu_uso_pct')].reconocida").value(true))
                    .andExpect(jsonPath("$.metricas[?(@.codigo == 'temperatura')].reconocida").value(false))
                    .andExpect(jsonPath("$.faltantes").value(Matchers.containsInAnyOrder("memoria_uso_pct", "disco_uso_pct")));
        }

        @Test
        @DisplayName("Probar una versión de análisis en un servidor revisa su salida sin registrar una evaluación")
        void probarAnalisis() throws Exception {
            Integer version = jdbc.queryForObject("select v.id_script_version from script_version v join script_implementacion "
                    + "using (id_script_implementacion) join script s using (id_script) where s.tipo = 'ANALISIS'", Integer.class);

            mvc.perform(post("/api/scripts/versiones/{id}/prueba", version).with(como(admin)).contentType("application/json")
                            .content("{\"idServidor\":%d}".formatted(servidor.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.correcto").value(true))
                    .andExpect(jsonPath("$.revision.valida").value(true));
            assertThat(contar("select count(*) from evaluacion")).isZero();
            assertThat(contar("select count(*) from auditoria where operacion = 'PROBAR_SCRIPT_ANALISIS'")).isEqualTo(1);

            Integer mantenimiento = jdbc.queryForObject("select v.id_script_version from script_version v join script_implementacion "
                    + "using (id_script_implementacion) join script s using (id_script) where s.nombre = 'Liberar archivos temporales'",
                    Integer.class);
            mvc.perform(post("/api/scripts/versiones/{id}/prueba", mantenimiento).with(como(admin)).contentType("application/json")
                            .content("{\"idServidor\":%d}".formatted(servidor.getId())))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("CP074: una tarea activa exige un script de mantenimiento con versión habilitada y al menos un umbral")
        void reglasDeTareas() throws Exception {
            Integer analisis = jdbc.queryForObject("select id_script from script where tipo = 'ANALISIS'", Integer.class);

            mvc.perform(post("/api/tareas").with(como(admin)).contentType("application/json")
                            .content("{\"idScript\":%d,\"nombre\":\"Mal\",\"nivelAfectacion\":\"PARCIAL\",\"prioridad\":1,"
                                    .formatted(analisis) + "\"duracionEstimadaMinutos\":5,\"activo\":true,\"idsUmbrales\":[]}"))
                    .andExpect(status().isUnprocessableEntity());
            mvc.perform(post("/api/tareas").with(como(admin)).contentType("application/json")
                            .content("{\"idScript\":%d,\"nombre\":\"Larga\",\"nivelAfectacion\":\"PARCIAL\",\"prioridad\":1,"
                                    .formatted(analisis) + "\"duracionEstimadaMinutos\":61,\"activo\":true,\"idsUmbrales\":[]}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.mensaje").value(Matchers.containsString("(60 min)")));
            mvc.perform(get("/api/tareas").with(como(operador)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].sistemasCompatibles[0]").value("Ubuntu 24.04"));
        }
    }

    @Nested
    @DisplayName("Seguimiento e historial (RF33-RF36, RF53, R3.4)")
    class Seguimiento {

        @Test
        @DisplayName("El historial de ejecución lista cada tarea con versión, cuenta, código y duración")
        void historialDeEjecuciones() throws Exception {
            lanzarAutorizada(servidor);
            orquestador.drenar();
            Integer tarea = jdbc.queryForObject("select id_mop_tarea from mop_tarea where orden_ejecucion = 1", Integer.class);

            mvc.perform(get("/api/automatizacion/ejecuciones").param("estado", "FINALIZADA").with(como(operador)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElementos").value(2))
                    .andExpect(jsonPath("$.contenido[0].servidor").value("srv-prueba"))
                    .andExpect(jsonPath("$.contenido[0].numeroVersion").value(1))
                    .andExpect(jsonPath("$.contenido[0].cuenta").value("Cuenta de pruebas"))
                    .andExpect(jsonPath("$.contenido[0].codigoRetorno").value(0));
            mvc.perform(get("/api/automatizacion/ejecuciones/{id}", tarea).with(como(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.salida").value(Matchers.containsString("[simulación]")));
        }

        @Test
        @DisplayName("RF34, RF35: la sala filtra por etapa y texto, y la cola muestra la posición")
        void salaYCola() throws Exception {
            Integer id = lanzarAutorizada(servidor);

            mvc.perform(get("/api/automatizacion/sala").param("etapa", "AUTORIZACION").param("texto", "SRV-PRUEBA")
                            .with(como(operador)))
                    .andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.contenido[0].id").value(id));
            mvc.perform(get("/api/automatizacion/indicadores").with(como(operador)))
                    .andExpect(jsonPath("$.AUTORIZACION").value(1));
            jdbc.update("update orden set estado = 'EN_COLA' where id_orden = ?", id);
            jdbc.update("update orden_detalle set estado = 'EN_COLA' where id_orden = ?", id);
            mvc.perform(get("/api/automatizacion/cola").with(como(operador)))
                    .andExpect(jsonPath("$.contenido[0].posicion").value(1))
                    .andExpect(jsonPath("$.contenido[0].prioridad").value("ALTA"));
        }

        @Test
        @DisplayName("Incidencias por HTTP: listado, filtros y detalle; el responsable no accede")
        void incidenciasPorHttp() throws Exception {
            Integer id = lanzarAutorizada(crear("srv-fallo", "10.90.0.2"));
            orquestador.drenar();
            Integer incidencia = jdbc.queryForObject("select id_incidencia from incidencia", Integer.class);

            mvc.perform(get("/api/incidencias").param("estado", "ABIERTA").param("origen", "FALLO_EJECUCION")
                            .with(como(operador)))
                    .andExpect(jsonPath("$.totalElementos").value(1))
                    .andExpect(jsonPath("$.contenido[0].idOrden").value(id));
            mvc.perform(get("/api/incidencias").param("texto", "SRV-FALLO").with(como(admin)))
                    .andExpect(jsonPath("$.totalElementos").value(1));
            mvc.perform(get("/api/incidencias/{id}", incidencia).with(como(responsable))).andExpect(status().isForbidden());
            incidencias.atender(incidencia, new DatosAtencion("Diagnóstico", "Acciones", "Resuelto", true), actor);
            mvc.perform(get("/api/incidencias").param("estado", "ABIERTA").with(como(admin)))
                    .andExpect(jsonPath("$.totalElementos").value(0));
        }

        @Test
        @DisplayName("RF66: los cinco KPI coinciden con los ciclos y respuestas del periodo")
        void indicadores() throws Exception {
            Integer correcto = lanzarAutorizada(servidor);
            orquestador.drenar();
            validaciones.validar(correcto, true, "Conforme", new AlcanceUsuario(responsable.getId(), Rol.RESPONSABLE));
            Integer fallido = lanzarAutorizada(crear("srv-fallo", "10.90.0.2"));
            orquestador.drenar();
            Integer incidencia = jdbc.queryForObject("select i.id_incidencia from incidencia i join orden_detalle d "
                    + "using (id_orden_detalle) where d.id_orden = ?", Integer.class, fallido);
            jdbc.update("update incidencia set fecha_apertura = ? where id_incidencia = ?",
                    Timestamp.from(AHORA.minusSeconds(14400)), incidencia);
            incidencias.atender(incidencia, new DatosAtencion("Fallo simulado", "Control revisado", "Resuelto", true), actor);
            // fecha_alta la asigna PostgreSQL; se sitúa en el periodo del reloj fijo de esta prueba
            jdbc.update("update servidor set fecha_alta = ?", Timestamp.from(AHORA));

            mvc.perform(get("/api/automatizacion/kpis").with(como(operador)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fechaActualizacion").value(AHORA.toString()))
                    .andExpect(jsonPath("$.indicadores[0].valor").value(100.0))
                    .andExpect(jsonPath("$.indicadores[1].valor").value(50.0))
                    .andExpect(jsonPath("$.indicadores[2].valor").value(50.0))
                    .andExpect(jsonPath("$.indicadores[3].valor").value(100.0))
                    .andExpect(jsonPath("$.indicadores[4].valor").value(4.0))
                    .andExpect(jsonPath("$.actividadDiaria[-1].mantenimientos").value(2))
                    .andExpect(jsonPath("$.inventario[0].cantidad").value(2));
            mvc.perform(get("/api/automatizacion/kpis").param("desde", AHORA.toString())
                            .param("hasta", AHORA.minusSeconds(1).toString()).with(como(admin)))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        @DisplayName("RF67: CPU, memoria y ejecuciones se calculan de las evaluaciones y tareas reales")
        void graficos() throws Exception {
            mvc.perform(get("/api/automatizacion/graficos").with(como(admin)))
                    .andExpect(jsonPath("$.recursos.length()").value(24))
                    .andExpect(jsonPath("$.recursos[0].cpu").doesNotExist())
                    .andExpect(jsonPath("$.ejecuciones[-1].exito").value(0));
            lanzarAutorizada(servidor);
            lanzarAutorizada(crear("srv-fallo", "10.90.0.2"));
            orquestador.drenar();

            mvc.perform(get("/api/automatizacion/graficos").with(como(operador)))
                    .andExpect(jsonPath("$.recursos[-1].servidores").value(2))
                    .andExpect(jsonPath("$.recursos[-1].cpu").isNumber())
                    .andExpect(jsonPath("$.ejecuciones[-1].exito").value(2))
                    .andExpect(jsonPath("$.ejecuciones[-1].error").value(1));
        }
    }

    @Nested
    @DisplayName("Alertas, política y demostración")
    class Configuracion {

        @Test
        @DisplayName("RF69: detalle, contador, lectura individual y de todas, siempre del destinatario")
        void alertas() throws Exception {
            alertas.emitir(DatosAlerta.de(TipoAlerta.ADVERTENCIA_UMBRAL, "Disco por encima del límite",
                    "Mensaje completo\nRevise las métricas.").servidor(servidor.getId()), Set.of(responsable.getId()));
            alertas.emitir(DatosAlerta.de(TipoAlerta.CONDICION_CRITICA, "Otra", "Otra"), Set.of(responsable.getId()));
            Integer id = jdbc.queryForObject("select min(id_alerta_destinatario) from alerta_destinatario where id_usuario = ?",
                    Integer.class, responsable.getId());

            mvc.perform(get("/api/alertas/{id}", id).with(como(responsable)))
                    .andExpect(jsonPath("$.mensaje").value("Mensaje completo\nRevise las métricas."));
            mvc.perform(get("/api/alertas/{id}", id).with(como(admin))).andExpect(status().isNotFound());
            mvc.perform(post("/api/alertas/{id}/lectura", id).with(como(responsable))).andExpect(status().isNoContent());
            mvc.perform(get("/api/alertas").param("soloNoLeidas", "true").with(como(responsable)))
                    .andExpect(jsonPath("$.totalElementos").value(1));
            mvc.perform(post("/api/alertas/lectura").with(como(responsable))).andExpect(jsonPath("$.leidas").value(1));
            mvc.perform(get("/api/alertas/pendientes").with(como(responsable))).andExpect(jsonPath("$.pendientes").value(0));
        }

        @Test
        @DisplayName("RF43: editar una celda conserva las demás; el operador no la modifica")
        void celdaDePolitica() throws Exception {
            ReglaPoliticaCambio regla = politicas.listar(PageRequest.of(0, 100)).stream()
                    .filter(r -> r.getAfectacion() == NivelAfectacion.PARCIAL).findFirst().orElseThrow();
            String cuerpo = ("{\"idEntorno\":%d,\"nivelAfectacion\":\"PARCIAL\",\"requiereResponsable\":true,"
                    + "\"requiereAdministrador\":false,\"activo\":true,\"notificaciones\":[\"OPERADOR\"],\"versionEsperada\":\"%s\"}")
                    .formatted(entorno, regla.getFechaActualizacion());

            mvc.perform(put("/api/politicas-cambio/{id}", regla.getId()).with(como(operador))
                    .contentType("application/json").content(cuerpo)).andExpect(status().isForbidden());
            mvc.perform(put("/api/politicas-cambio/{id}", regla.getId()).with(como(admin))
                            .contentType("application/json").content(cuerpo))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notificaciones[0]").value("OPERADOR"));
            mvc.perform(get("/api/politicas-cambio").with(como(operador))).andExpect(jsonPath("$.totalElementos").value(3));
            assertThat(contar("select count(*) from auditoria where operacion = 'ACTUALIZAR_POLITICA_CAMBIO'")).isEqualTo(1);
        }

        @Test
        @DisplayName("La demostración es idempotente: una segunda carga no agrega nada")
        void demostracionIdempotente() throws Exception {
            mvc.perform(post("/api/automatizacion/demostracion").with(como(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isEmpty());
            assertThat(contar("select count(*) from script")).isEqualTo(3);
            assertThat(contar("select count(*) from tarea")).isEqualTo(2);
        }

        @Test
        @DisplayName("La bitácora de una orden ajena al responsable no se expone por otro camino")
        void alcanceDeLaBitacora() throws Exception {
            Usuario otro = usuarios.crear("otro.resp", "Otro responsable", "Clave-prueba-5", Rol.RESPONSABLE,
                    Actor.sistema("PRUEBA"));
            Integer id = lanzar(servidor);

            mvc.perform(post("/api/ordenes/{id}/autorizacion", id).with(como(otro)).contentType("application/json")
                            .content("{\"decision\":\"APROBADA\",\"motivo\":\"Intento\"}"))
                    .andExpect(status().isNotFound());
        }
    }
}
