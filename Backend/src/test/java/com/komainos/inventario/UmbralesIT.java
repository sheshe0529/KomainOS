package com.komainos.inventario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.PruebaIntegracion;
import com.komainos.inventario.model.DatosServidor;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.NivelCriticidad.DatosNivelCriticidad;
import com.komainos.inventario.service.ServicioCatalogos;
import com.komainos.inventario.service.ServicioGrupo;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.seguridad.service.ServicioUsuario;
import com.komainos.shared.model.Actor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sin @Transactional: cada petición abre y cierra su propia transacción como en producción, así un mapeo que dependa de
 * una asociación sin cargar falla aquí (open-in-view está desactivado).
 */
@DisplayName("Métricas, listas de referencia y umbrales contra PostgreSQL (RF16, HU12, R2.4)")
class UmbralesIT extends PruebaIntegracion {

    @Autowired ServicioUsuario usuarios;
    @Autowired ServicioCatalogos catalogos;
    @Autowired ServicioServidor servidores;
    @Autowired ServicioGrupo grupos;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    private Usuario admin;
    private Usuario operador;
    private Usuario responsable;
    private Integer idProduccion;
    private Integer idAlta;
    private Integer bd01;
    private Integer bd02;
    private Integer des01;
    private Integer idGrupo;

    @BeforeEach
    void inventarioBase() {
        limpiarDatos();
        Actor sistema = Actor.sistema("PRUEBA");
        admin = usuarios.crear("admin.umb", "Administrador", "clave-segura-1", Rol.ADMINISTRADOR, sistema);
        operador = usuarios.crear("oper.umb", "Operador", "clave-segura-2", Rol.OPERADOR, sistema);
        responsable = usuarios.crear("resp.umb", "M. Herrera", "clave-segura-3", Rol.RESPONSABLE, sistema);
        Usuario otroResponsable = usuarios.crear("resp2.umb", "J. Paredes", "clave-segura-4", Rol.RESPONSABLE, sistema);
        idProduccion = catalogos.crearEntorno("Producción", null, sistema).getId();
        Integer idDesarrollo = catalogos.crearEntorno("Desarrollo", null, sistema).getId();
        idAlta = catalogos.crearCriticidad(new DatosNivelCriticidad("Alta", 1, 7, 30, 24, 24), sistema).getId();
        Integer idMedia = catalogos.crearCriticidad(new DatosNivelCriticidad("Media", 2, 14, 30, 24, 48), sistema).getId();
        Integer ubuntu = catalogos.crearSistemaOperativo("Ubuntu", FamiliaSistemaOperativo.LINUX, sistema).getId();
        Integer version = catalogos.agregarVersion(ubuntu, "22.04", sistema).getVersiones().getFirst().getId();
        Actor actorAdmin = Actor.usuario(admin.getId());
        bd01 = servidor("SERVPRODBDDV01", "10.30.0.1", version, idProduccion, idAlta, responsable, actorAdmin);
        bd02 = servidor("SERVPRODBDDV02", "10.30.0.2", version, idProduccion, idAlta, responsable, actorAdmin);
        des01 = servidor("SERVDESAPPV01", "10.30.0.3", version, idDesarrollo, idMedia, otroResponsable, actorAdmin);
        idGrupo = grupos.crear("Clúster BD", "Réplicas de la base de pagos", actorAdmin).getId();
        grupos.reemplazarIntegrantes(idGrupo, List.of(bd01, bd02), actorAdmin);
    }

    @AfterEach
    void limpiar() {
        limpiarDatos();
    }

    private Integer servidor(String hostname, String ip, Integer version, Integer entorno, Integer criticidad,
                             Usuario dueno, Actor actor) {
        return servidores.crear(new DatosServidor(hostname, ip, "DCNORTE", null, null, null, null, version, null,
                entorno, criticidad, dueno.getId(), null, List.of(), null, null, null), actor).getId();
    }

    private MockHttpServletRequestBuilder como(Usuario usuario, MockHttpServletRequestBuilder peticion) {
        return peticion.with(user(new UsuarioAutenticado(usuario))).contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions enviar(MockHttpServletRequestBuilder peticion, Object cuerpo) throws Exception {
        return mockMvc.perform(como(admin, peticion).content(json.writeValueAsString(cuerpo)));
    }

    private Integer id(ResultActions resultado) throws Exception {
        return json.readTree(resultado.andReturn().getResponse().getContentAsString()).get("id").asInt();
    }

    private JsonNode leer(Usuario usuario, MockHttpServletRequestBuilder peticion) throws Exception {
        return json.readTree(mockMvc.perform(como(usuario, peticion)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode umbralDe(JsonNode delServidor, String nombre) {
        for (JsonNode u : delServidor) {
            if (u.get("umbral").get("nombre").asText().equals(nombre)) {
                return u;
            }
        }
        throw new AssertionError("El servidor no tiene el umbral " + nombre);
    }

    private Integer metrica(String codigo, String nombre, String tipo, boolean esLista) throws Exception {
        return id(enviar(post("/api/metricas"), Map.of("codigo", codigo, "nombre", nombre, "tipoDato", tipo,
                "esLista", esLista)).andExpect(status().isCreated()));
    }

    private Integer excepcion(Integer idUmbral, String alcance, Integer idObjetivo, int advertencia, int critico)
            throws Exception {
        return id(enviar(post("/api/umbrales/{id}/excepciones", idUmbral), Map.of("alcance", alcance,
                "idObjetivo", idObjetivo, "valorAdvertencia", advertencia, "valorCritico", critico))
                .andExpect(status().isCreated()));
    }

    @Test
    @DisplayName("CP193/CP194/CP196/CP197: umbral numérico con excepciones en los cuatro alcances y su prioridad")
    void numericoConExcepciones() throws Exception {
        Integer cpu = metrica("cpu_uso_pct", "Uso de CPU", "DECIMAL", false);
        Integer umbral = id(enviar(post("/api/umbrales"), Map.of("idMetrica", cpu, "nombre", "CPU alta",
                "operador", "MAYOR", "valorAdvertencia", 80, "valorCritico", 90))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.valorAdvertencia").value(80)));
        excepcion(umbral, "CRITICIDAD", idAlta, 75, 85);
        Integer delEntorno = excepcion(umbral, "ENTORNO", idProduccion, 70, 80);
        excepcion(umbral, "GRUPO", idGrupo, 65, 75);
        excepcion(umbral, "SERVIDOR", bd01, 60, 70);

        // CP195: una por alcance, coherente con el operador y sobre catálogos vigentes
        enviar(post("/api/umbrales/{id}/excepciones", umbral), Map.of("alcance", "ENTORNO", "idObjetivo", idProduccion,
                "valorAdvertencia", 50)).andExpect(status().isConflict());
        enviar(post("/api/umbrales/{id}/excepciones", umbral), Map.of("alcance", "SERVIDOR", "idObjetivo", bd02,
                "valorAdvertencia", 90, "valorCritico", 70))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("coherentes")));

        JsonNode detalle = leer(operador, get("/api/umbrales/{id}", umbral));
        assertThat(detalle.get("excepciones")).extracting(e -> e.get("alcance").asText())
                .containsExactly("SERVIDOR", "GRUPO", "ENTORNO", "CRITICIDAD");
        assertThat(detalle.get("excepciones").get(0).get("objetivo").get("nombre").asText()).isEqualTo("SERVPRODBDDV01");

        // CP193: el servidor manda; en BD02 rige el entorno y dentro del grupo, el grupo
        JsonNode deBd01 = umbralDe(leer(responsable, get("/api/servidores/{id}/umbrales", bd01)), "CPU alta");
        assertThat(deBd01.get("origen").asText()).isEqualTo("SERVIDOR");
        assertThat(deBd01.get("aplicado").get("valorCritico").asInt()).isEqualTo(70);
        assertThat(deBd01.get("global").get("valorCritico").asInt()).isEqualTo(90);
        assertThat(deBd01.get("enGrupos")).isEmpty();
        JsonNode deBd02 = umbralDe(leer(responsable, get("/api/servidores/{id}/umbrales", bd02)), "CPU alta");
        assertThat(deBd02.get("origen").asText()).isEqualTo("ENTORNO");
        assertThat(deBd02.get("enGrupos").get(0).get("grupo").get("nombre").asText()).isEqualTo("Clúster BD");
        assertThat(deBd02.get("enGrupos").get(0).get("valores").get("valorCritico").asInt()).isEqualTo(75);
        assertThat(umbralDe(leer(admin, get("/api/servidores/{id}/umbrales", des01)), "CPU alta")
                .get("origen").asText()).isEqualTo("GLOBAL");

        // CP194/CP197: la misma medición cambia de nivel según el contexto; probar no registra nada
        int evaluacionesAntes = jdbc.queryForObject("select count(*) from evaluacion_umbral", Integer.class);
        mockMvc.perform(como(responsable, get("/api/servidores/{id}/umbrales/{u}/prueba", bd02, umbral)).param("valor", "78"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("ADVERTENCIA"))
                .andExpect(jsonPath("$.origen").value("ENTORNO"));
        mockMvc.perform(como(responsable, get("/api/servidores/{id}/umbrales/{u}/prueba", bd02, umbral))
                        .param("valor", "78").param("idGrupo", idGrupo.toString()))
                .andExpect(jsonPath("$.nivel").value("CRITICO"))
                .andExpect(jsonPath("$.origen").value("GRUPO"));
        mockMvc.perform(como(admin, get("/api/servidores/{id}/umbrales/{u}/prueba", des01, umbral))
                        .param("valor", "78").param("idGrupo", idGrupo.toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("no integra")));
        assertThat(jdbc.queryForObject("select count(*) from evaluacion_umbral", Integer.class)).isEqualTo(evaluacionesAntes);

        // CP196: sin la del entorno, en BD02 rige la criticidad
        mockMvc.perform(como(admin, post("/api/umbrales/{id}/excepciones/{e}/desactivacion", umbral, delEntorno)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        mockMvc.perform(como(admin, get("/api/servidores/{id}/umbrales/{u}/prueba", bd02, umbral)).param("valor", "78"))
                .andExpect(jsonPath("$.nivel").value("ADVERTENCIA"))
                .andExpect(jsonPath("$.origen").value("CRITICIDAD"))
                .andExpect(jsonPath("$.aplicado.valorCritico").value(85));

        // CP198: el operador no cambia mientras haya excepciones
        enviar(put("/api/umbrales/{id}", umbral), Map.of("idMetrica", cpu, "nombre", "CPU alta", "operador", "MENOR",
                "valorAdvertencia", 20, "valorCritico", 10))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("antes de cambiar su operador")));
        enviar(put("/api/metricas/{id}", cpu), Map.of("nombre", "Uso de CPU", "tipoDato", "INTEGER", "esLista", false))
                .andExpect(status().isUnprocessableEntity());

        // CP200/RNF06: cada cambio queda auditado; la excepción de servidor enlaza el servidor
        assertThat(jdbc.queryForObject("select count(*) from auditoria where operacion = 'CREAR_EXCEPCION_UMBRAL'",
                Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                select count(*) from auditoria where operacion = 'CREAR_EXCEPCION_UMBRAL' and id_servidor = ?""",
                Integer.class, bd01)).isOne();
        assertThat(jdbc.queryForObject("""
                select count(*) from auditoria where operacion = 'DESACTIVAR_EXCEPCION_UMBRAL' and id_usuario = ?""",
                Integer.class, admin.getId())).isOne();
    }

    @Test
    @DisplayName("CP187/CP188/CP190/CP191/CP197: umbral de lista con faltantes, sobrantes y lista propia por servidor")
    void umbralDeLista() throws Exception {
        Integer puertos = metrica("puertos_escucha", "Puertos en escucha", "INTEGER", true);
        Integer web = id(enviar(post("/api/listas-referencia"), Map.of("nombre", "Puertos web", "tipoDato", "INTEGER",
                "elementos", List.of("443", "0080", "22", "80")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.elementos").value(org.hamcrest.Matchers.contains("22", "80", "443"))));
        Integer servicios = id(enviar(post("/api/listas-referencia"), Map.of("nombre", "Servicios base", "tipoDato", "STRING",
                "ignoraMayusculas", true, "elementos", List.of("sshd", "crond"))).andExpect(status().isCreated()));

        // CP192: la lista debe ser del tipo de la métrica
        enviar(post("/api/umbrales"), Map.of("idMetrica", puertos, "nombre", "Puertos", "idListaReferencia", servicios,
                "nivelFaltante", "CRITICO"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("es de tipo texto")));
        Integer umbral = id(enviar(post("/api/umbrales"), Map.of("idMetrica", puertos, "nombre", "Puertos esperados",
                "idListaReferencia", web, "nivelFaltante", "CRITICO", "nivelSobrante", "ADVERTENCIA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lista.elementos.length()").value(3))
                .andExpect(jsonPath("$.operador").doesNotExist()));

        // CP188: modificar la lista conserva el elemento retirado con su fecha de baja
        enviar(put("/api/listas-referencia/{id}", web), Map.of("nombre", "Puertos web", "tipoDato", "INTEGER",
                "elementos", List.of("22", "443", "8443")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.elementos").value(org.hamcrest.Matchers.contains("22", "443", "8443")));
        assertThat(jdbc.queryForList("select valor from lista_referencia_item where id_lista_referencia = ? and fecha_baja is not null",
                String.class, web)).containsExactly("80");
        assertThat(jdbc.queryForObject("select count(*) from lista_referencia_item where id_lista_referencia = ?",
                Integer.class, web)).isEqualTo(4);

        // CP197: falta 8443 (crítico) y sobra 3306 (advertencia): gana el peor
        mockMvc.perform(como(admin, get("/api/servidores/{id}/umbrales/{u}/prueba", des01, umbral))
                        .param("elementos", "22, 443;3306"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nivel").value("CRITICO"))
                .andExpect(jsonPath("$.faltantes").value(org.hamcrest.Matchers.contains("8443")))
                .andExpect(jsonPath("$.sobrantes").value(org.hamcrest.Matchers.contains("3306")));

        // CP193: el servidor de base de datos tiene su propia lista
        Integer bd = id(enviar(post("/api/listas-referencia"), Map.of("nombre", "Puertos base de datos",
                "tipoDato", "INTEGER", "elementos", List.of("22", "5432"))).andExpect(status().isCreated()));
        enviar(post("/api/umbrales/{id}/excepciones", umbral), Map.of("alcance", "SERVIDOR", "idObjetivo", bd01,
                "idListaReferencia", bd, "motivo", "Solo PostgreSQL"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lista.nombre").value("Puertos base de datos"));
        mockMvc.perform(como(responsable, get("/api/servidores/{id}/umbrales/{u}/prueba", bd01, umbral))
                        .param("elementos", "5432\n22"))
                .andExpect(jsonPath("$.nivel").value("NORMAL"))
                .andExpect(jsonPath("$.origen").value("SERVIDOR"));
        JsonNode deBd01 = umbralDe(leer(responsable, get("/api/servidores/{id}/umbrales", bd01)), "Puertos esperados");
        assertThat(deBd01.get("aplicado").get("lista").get("elementos")).extracting(JsonNode::asText)
                .containsExactly("22", "5432");
        assertThat(deBd01.get("aplicado").get("nivelSobrante").asText()).isEqualTo("ADVERTENCIA");

        // CP190: las listas en uso no se eliminan; se pueden desactivar
        mockMvc.perform(como(admin, delete("/api/listas-referencia/{id}", web)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("Puertos esperados")));
        mockMvc.perform(como(admin, delete("/api/listas-referencia/{id}", bd)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("excepciones")));
        mockMvc.perform(como(admin, post("/api/listas-referencia/{id}/desactivacion", web)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        mockMvc.perform(como(admin, delete("/api/listas-referencia/{id}", servicios))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from lista_referencia_item where id_lista_referencia = ?",
                Integer.class, servicios)).isZero();
        JsonNode listas = leer(operador, get("/api/listas-referencia"));
        assertThat(listas).extracting(l -> l.get("nombre").asText()).containsExactly("Puertos base de datos", "Puertos web");
        assertThat(listas.get(1).get("umbrales").asInt()).isOne();
        assertThat(listas.get(0).get("excepciones").asInt()).isOne();
    }

    @Test
    @DisplayName("CP199: el operador consulta sin modificar y el responsable solo ve sus servidores")
    void alcancePorRol() throws Exception {
        Integer cpu = metrica("cpu_uso_pct", "Uso de CPU", "DECIMAL", false);
        Integer umbral = id(enviar(post("/api/umbrales"), Map.of("idMetrica", cpu, "nombre", "CPU alta",
                "operador", "MAYOR_IGUAL", "valorCritico", 95)).andExpect(status().isCreated()));

        mockMvc.perform(como(operador, get("/api/umbrales"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("CPU alta"));
        mockMvc.perform(como(operador, get("/api/metricas"))).andExpect(status().isOk());
        mockMvc.perform(como(operador, post("/api/umbrales/{id}/desactivacion", umbral))).andExpect(status().isForbidden());
        mockMvc.perform(como(operador, post("/api/metricas")).content("""
                {"codigo":"ram","nombre":"RAM","tipoDato":"DECIMAL"}""")).andExpect(status().isForbidden());
        mockMvc.perform(como(responsable, get("/api/umbrales"))).andExpect(status().isForbidden());
        mockMvc.perform(como(responsable, get("/api/listas-referencia"))).andExpect(status().isForbidden());

        // Un servidor ajeno responde 404 y no 403 para no confirmar que existe
        mockMvc.perform(como(responsable, get("/api/servidores/{id}/umbrales", bd01))).andExpect(status().isOk());
        mockMvc.perform(como(responsable, get("/api/servidores/{id}/umbrales", des01))).andExpect(status().isNotFound());
        mockMvc.perform(como(responsable, get("/api/servidores/{id}/umbrales/{u}/prueba", des01, umbral))
                .param("valor", "99")).andExpect(status().isNotFound());

        // Un umbral desactivado deja de aplicar al servidor
        mockMvc.perform(como(admin, post("/api/umbrales/{id}/desactivacion", umbral))).andExpect(status().isOk());
        assertThat(leer(responsable, get("/api/servidores/{id}/umbrales", bd01))).isEmpty();
        mockMvc.perform(como(admin, delete("/api/umbrales/{id}", umbral))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from auditoria where operacion = 'ELIMINAR_UMBRAL'", Integer.class))
                .isOne();
    }
}
