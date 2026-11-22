package com.komainos.mantenimiento;

import com.jayway.jsonpath.JsonPath;
import com.komainos.inventario.model.Servidor;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.MotivoCierre;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * «Ver actividad» de la orden y del historial de ejecución (DEC-59): cada conexión, la comprobación de acceso, los
 * análisis y las tareas quedan con su resultado y el motivo de cada fallo. El reloj de las pruebas está fijo, así que
 * todos los eventos comparten la hora y su orden lo da la secuencia de registro.
 */
@DisplayName("Actividad de las ejecuciones (DEC-59)")
class ActividadIT extends BaseAutomatizacionIT {

    @Test
    @DisplayName("Una ejecución correcta deja la conexión, el acceso, cada tarea y los análisis con su resultado")
    void ejecucionCorrecta() throws Exception {
        Integer id = lanzarAutorizada(servidor);
        orquestador.drenar();
        assertThat(estado(id)).isEqualTo(EstadoOrden.PENDIENTE_VALIDACION_FUNCIONAL);

        String cuerpo = cuerpo(get("/api/ordenes/{id}/actividad", id), admin);
        List<Map<String, Object>> eventos = JsonPath.read(cuerpo, "$");

        assertThat(registradas(eventos)).containsExactly(
                "Evaluación previa iniciada",
                "Conexión iniciada",
                "Acceso verificado",
                "Evaluación previa completada",
                "Ejecución iniciada",
                "Comprobación de acceso iniciada",
                "Acceso verificado",
                "Tarea «Liberar archivos temporales» iniciada",
                "Tarea «Liberar archivos temporales» completada",
                "Tarea «Revisar consumo de memoria» iniciada",
                "Tarea «Revisar consumo de memoria» completada",
                "Ejecución completada",
                "Análisis posterior iniciado",
                "Conexión iniciada",
                "Acceso verificado",
                "Análisis posterior completado");
        // Los cambios de estado de la orden se intercalan con la actividad
        assertThat(acciones(eventos)).contains("Autorización solicitada", "Orden autorizada",
                "Validación técnica iniciada", "Validación funcional solicitada");

        // Sin fase (DEC-60): los eventos de un análisis se reconocen por su evaluación
        Map<String, Object> conexion = ultimo(eventos, "Conexión iniciada", e -> e.get("idEvaluacion") != null);
        assertThat(conexion.get("nivel")).isEqualTo("INFO");
        assertThat((String) conexion.get("detalle"))
                .startsWith("srv-prueba (10.90.0.1) por SSH (puerto ")
                .contains("cuenta «Cuenta de pruebas»: usuario svc_komainos con contraseña");
        assertThat(conexion.get("script")).isEqualTo(ANALISIS + " v1");

        Map<String, Object> acceso = ultimo(eventos, "Acceso verificado", e -> e.get("idEvaluacion") == null);
        assertThat(acceso.get("nivel")).isEqualTo("INFO");
        assertThat(acceso.get("estado")).isEqualTo("COMPLETADO");
        assertThat((String) acceso.get("detalle")).contains("con privilegios de administrador")
                .doesNotContain("KOMAINOS_ADMINISTRADOR");

        Map<String, Object> tarea = evento(eventos, "Tarea «Liberar archivos temporales» completada");
        assertThat(tarea.get("detalle")).isEqualTo("Código de salida: 0 | Duración: 0 s");
        assertThat(tarea.get("script")).isEqualTo(LIMPIEZA + " v1");
        assertThat(tarea.get("servidor")).isEqualTo("srv-prueba");
        assertThat(tarea.get("idTarea")).isNotNull();
        assertThat((String) evento(eventos, "Tarea «Liberar archivos temporales» iniciada").get("detalle"))
                .startsWith("Paso 1 de 2 | srv-prueba (10.90.0.1)");
        assertThat(evento(eventos, "Ejecución completada").get("detalle"))
                .isEqualTo("2 de 2 tareas completadas");
        assertThat(evento(eventos, "Análisis posterior completado").get("idEvaluacion")).isNotNull();
        assertThat(eventos).allSatisfy(e -> assertThat(e).doesNotContainKey("fase"));
        // DEC-60: cada evento guarda un solo origen, sin servidor ni fase; el servidor sale de ese origen
        assertThat(jdbc.queryForObject("""
                select count(*) from actividad_ejecucion
                where num_nonnulls(id_orden_detalle, id_evaluacion, id_mop_tarea) <> 1""", Integer.class)).isZero();
        assertThat(jdbc.queryForList("""
                select case when id_evaluacion is not null then 'evaluacion'
                            when id_mop_tarea is not null then 'tarea' else 'detalle' end
                from actividad_ejecucion order by 1""", String.class))
                .containsExactly("detalle", "detalle", "detalle", "detalle", "evaluacion", "evaluacion", "evaluacion",
                        "evaluacion", "evaluacion", "evaluacion", "evaluacion", "evaluacion", "tarea", "tarea", "tarea",
                        "tarea");
        assertThat((String) evento(eventos, "Evaluación previa completada").get("detalle"))
                .startsWith("Resultado del análisis: Advertencia | ");
        // RNF11: ni la contraseña de la cuenta ni las variables de conexión salen en la actividad
        assertThat(cuerpo).doesNotContain("secreto-solo-para-pruebas").doesNotContain("ansible_");
    }

    @Test
    @DisplayName("Una tarea fallida queda en ERROR con su código y su motivo, y el servidor con las tareas sin iniciar")
    void falloDeTarea() throws Exception {
        Integer id = lanzarAutorizada(crear("srv-fallo", "10.90.0.2"));
        orquestador.drenar();
        assertThat(estado(id)).isEqualTo(EstadoOrden.INCIDENCIA);

        List<Map<String, Object>> eventos = actividad(id, admin);

        assertThat(registradas(eventos)).containsSubsequence(
                        "Acceso verificado",
                        "Tarea «Liberar archivos temporales» iniciada",
                        "Tarea «Liberar archivos temporales» ha fallado",
                        "Ejecución fallida")
                .doesNotContain("Tarea «Revisar consumo de memoria» iniciada");
        Map<String, Object> fallo = evento(eventos, "Tarea «Liberar archivos temporales» ha fallado");
        assertThat(fallo.get("nivel")).isEqualTo("ERROR");
        assertThat(fallo.get("estado")).isEqualTo("FALLIDO");
        assertThat(fallo.get("detalle")).isEqualTo("Código de salida: 1 | Duración: 0 s");
        // La categoría al principio permite al panel ofrecer el diagnóstico
        assertThat((String) fallo.get("error")).startsWith("Error de ejecución: ");
        assertThat(evento(eventos, "Ejecución fallida").get("detalle"))
                .isEqualTo("0 de 2 tareas completadas | 1 sin iniciar");
        Map<String, Object> incidencia = evento(eventos, "Incidencia registrada");
        assertThat(incidencia.get("detalle")).isEqualTo("Fallo de ejecución");
        assertThat(incidencia.get("servidor")).isEqualTo("srv-fallo");
    }

    @Test
    @DisplayName("El historial de ejecución muestra lo que precedió a la tarea en su servidor, en orden cronológico")
    void actividadDeUnaEjecucion() throws Exception {
        lanzarAutorizada(crear("srv-fallo", "10.90.0.2"));
        orquestador.drenar();
        Integer fallida = contar("select id_mop_tarea from mop_tarea where orden_ejecucion = 1");
        Integer sinIniciar = contar("select id_mop_tarea from mop_tarea where orden_ejecucion = 2");

        assertThat(acciones(JsonPath.read(cuerpo(get("/api/automatizacion/ejecuciones/{id}/actividad", fallida),
                operador), "$"))).containsExactly(
                "Ejecución iniciada",
                "Comprobación de acceso iniciada",
                "Acceso verificado",
                "Tarea «Liberar archivos temporales» iniciada",
                "Tarea «Liberar archivos temporales» ha fallado",
                "Ejecución fallida");
        // La que no llegó a iniciarse solo tiene lo que ocurrió en su servidor
        assertThat(acciones(JsonPath.read(cuerpo(get("/api/automatizacion/ejecuciones/{id}/actividad", sinIniciar),
                admin), "$"))).noneMatch(a -> a.startsWith("Tarea"));
        mvc.perform(get("/api/automatizacion/ejecuciones/{id}/actividad", fallida).with(como(responsable)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DEC-57: un acceso sin privilegios de administrador se advierte antes de que fallen las tareas")
    void accesoSinPrivilegios() throws Exception {
        Integer id = lanzarAutorizada(crear("srv-sin-permisos", "10.90.0.4"));
        orquestador.drenar();

        List<Map<String, Object>> eventos = actividad(id, admin);

        Map<String, Object> acceso = evento(eventos, "Acceso verificado sin privilegios de administrador");
        assertThat(acceso.get("nivel")).isEqualTo("WARNING");
        assertThat(acceso.get("estado")).isEqualTo("COMPLETADO");
        assertThat((String) acceso.get("detalle")).contains("sin privilegios de administrador");
        assertThat((String) evento(eventos, "Tarea «Liberar archivos temporales» ha fallado").get("error"))
                .startsWith("Permisos insuficientes: ");
    }

    @Test
    @DisplayName("Si la comprobación de acceso falla al ejecutar, no se inicia ninguna tarea y queda el motivo")
    void comprobacionFallida() throws Exception {
        Servidor s = crear("srv-cambiado", "10.90.0.5");
        Integer id = lanzarAutorizada(s);
        // Analizado con acceso; antes de ejecutar, la elevación deja de funcionar (el escenario lo decide el hostname)
        jdbc.update("update servidor set hostname = 'srv-elevacion-fallida' where id_servidor = ?", s.getId());
        orquestador.drenar();

        List<Map<String, Object>> eventos = actividad(id, admin);

        assertThat(registradas(eventos)).containsSubsequence(
                        "Ejecución iniciada",
                        "Comprobación de acceso iniciada",
                        "Comprobación de acceso fallida",
                        "Ejecución fallida")
                .noneMatch(a -> a.startsWith("Tarea"));
        Map<String, Object> fallo = evento(eventos, "Comprobación de acceso fallida");
        assertThat(fallo.get("nivel")).isEqualTo("ERROR");
        assertThat(fallo.get("error")).isEqualTo("Permisos insuficientes: Incorrect sudo password (simulado)");
        assertThat(evento(eventos, "Ejecución fallida").get("detalle"))
                .isEqualTo("0 de 2 tareas completadas | 2 sin iniciar");
    }

    @Test
    @DisplayName("Un análisis previo sin acceso cierra la orden sin implementación y la actividad dice por qué (DEC-61)")
    void analisisFallido() throws Exception {
        Integer id = lanzar(crear("srv-elevacion-fallida", "10.90.0.6"));
        assertThat(estado(id)).isEqualTo(EstadoOrden.CERRADA);
        assertThat(motivo(id)).isEqualTo(MotivoCierre.SIN_IMPLEMENTACION);

        List<Map<String, Object>> eventos = actividad(id, admin);

        assertThat(registradas(eventos)).containsExactly(
                "Evaluación previa iniciada",
                "Conexión iniciada",
                "Evaluación previa ha fallado");
        Map<String, Object> fallo = evento(eventos, "Evaluación previa ha fallado");
        assertThat(fallo.get("nivel")).isEqualTo("ERROR");
        assertThat(fallo.get("error")).isEqualTo("Permisos insuficientes: Incorrect sudo password (simulado)");
        // DEC-61: la categoría llega tipificada; el panel no la deduce del texto
        assertThat(fallo.get("tipoFallo")).isEqualTo("PERMISOS");
        assertThat(contar("select count(*) from actividad_ejecucion a join evaluacion e using (id_evaluacion) "
                + "join orden_detalle d on d.id_orden_detalle = e.id_orden_detalle "
                + "where d.id_orden = ? and a.tipo_fallo = 'PERMISOS'", id)).isEqualTo(1);
        Map<String, Object> bloqueo = evento(eventos, "Orden cerrada: sin implementación");
        assertThat(bloqueo.get("nivel")).isEqualTo("ERROR");
        assertThat((String) bloqueo.get("detalle")).contains("srv-elevacion-fallida");
        // El siguiente paso no repite el error: lo explica la etapa de evaluación con su diagnóstico
        String ficha = cuerpo(get("/api/ordenes/{id}/automatizacion", id), admin);
        assertThat((String) JsonPath.read(ficha, "$.siguientePaso"))
                .startsWith("Ciclo cerrado sin implementación: la evaluación previa no se completó")
                .doesNotContain("Incorrect sudo password");
        assertThat((String) JsonPath.read(ficha, "$.evaluaciones[0].error"))
                .isEqualTo("Permisos insuficientes: Incorrect sudo password (simulado)");
    }

    @Test
    @DisplayName("Lo ejecutado antes del registro de actividad se reconstruye con el inicio y el fin que ya guardaba")
    void ejecucionAnteriorAlRegistro() throws Exception {
        Integer id = lanzarAutorizada(crear("srv-fallo", "10.90.0.2"));
        orquestador.drenar();
        jdbc.update("delete from actividad_ejecucion");

        List<Map<String, Object>> eventos = actividad(id, admin);

        assertThat(registradas(eventos)).isEmpty();
        assertThat(acciones(eventos)).contains(
                "Evaluación previa iniciada",
                "Evaluación previa completada",
                "Tarea «Liberar archivos temporales» iniciada",
                "Tarea «Liberar archivos temporales» ha fallado",
                "Incidencia registrada");
        Map<String, Object> fallo = evento(eventos, "Tarea «Liberar archivos temporales» ha fallado");
        assertThat(fallo.get("detalle")).isEqualTo("Código de salida: 1");
        assertThat((String) fallo.get("error")).startsWith("Error de ejecución: ");
        assertThat((String) fallo.get("clave")).startsWith("tarea-");
    }

    @Test
    @DisplayName("El responsable ve la actividad de sus órdenes; la de un servidor ajeno responde 404")
    void alcance() throws Exception {
        Integer id = lanzarAutorizada(servidor);
        orquestador.drenar();
        Usuario ajeno = usuarios.crear("ajeno.act", "Otro responsable", "Clave-prueba-4", Rol.RESPONSABLE, actor);

        assertThat(actividad(id, responsable)).isNotEmpty();
        mvc.perform(get("/api/ordenes/{id}/actividad", id).with(como(ajeno))).andExpect(status().isNotFound());
        mvc.perform(get("/api/ordenes/{id}/actividad", 999_999).with(como(admin))).andExpect(status().isNotFound());
    }

    private List<Map<String, Object>> actividad(Integer idOrden, Usuario usuario) throws Exception {
        return JsonPath.read(cuerpo(get("/api/ordenes/{id}/actividad", idOrden), usuario), "$");
    }

    private String cuerpo(MockHttpServletRequestBuilder peticion, Usuario usuario) throws Exception {
        return mvc.perform(peticion.with(como(usuario))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static List<String> acciones(List<Map<String, Object>> eventos) {
        return eventos.stream().map(e -> (String) e.get("accion")).toList();
    }

    /** Los eventos que guardó el registro, en el orden en que ocurrieron (la orden los entrega del más reciente) */
    private static List<String> registradas(List<Map<String, Object>> eventos) {
        List<String> acciones = new ArrayList<>(eventos.stream()
                .filter(e -> ((String) e.get("clave")).startsWith("actividad-"))
                .map(e -> (String) e.get("accion")).toList());
        Collections.reverse(acciones);
        return acciones;
    }

    private static Map<String, Object> evento(List<Map<String, Object>> eventos, String accion) {
        return eventos.stream().filter(e -> accion.equals(e.get("accion"))).findFirst()
                .orElseThrow(() -> new AssertionError("Sin el evento «%s» en %s".formatted(accion, acciones(eventos))));
    }

    /** El más reciente de esa acción que cumple la condición */
    private static Map<String, Object> ultimo(List<Map<String, Object>> eventos, String accion,
                                              java.util.function.Predicate<Map<String, Object>> condicion) {
        return eventos.stream().filter(e -> accion.equals(e.get("accion")) && condicion.test(e)).findFirst()
                .orElseThrow(() -> new AssertionError("Sin el evento «%s»".formatted(accion)));
    }
}
