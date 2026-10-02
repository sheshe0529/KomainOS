package com.komainos.inventario;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.PruebaIntegracion;
import com.komainos.inventario.model.DatosServidor;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.NivelCriticidad.DatosNivelCriticidad;
import com.komainos.inventario.service.ServicioCatalogos;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** No es @Transactional: la importación registra cada fila en su propia transacción */
@DisplayName("Importación y exportación del inventario (RF12, RF13, HU08, HU09)")
class IntercambioInventarioIT extends PruebaIntegracion {

    /** Encabezado con etiquetas legibles, en otro orden y con una columna que no se importa */
    private static final String ENCABEZADO =
            "Hostname,Dirección IP,Sistema operativo,Versión,Entorno,Criticidad,Responsable,Descripción,Estado\n";

    @Autowired ServicioUsuario usuarios;
    @Autowired ServicioCatalogos catalogos;
    @Autowired ServicioServidor servidores;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    private UsuarioAutenticado comoAdmin;
    private UsuarioAutenticado comoResponsable;
    private UsuarioAutenticado comoOperador;
    private Integer idExistente;

    @BeforeEach
    void preparar() {
        limpiarDatos();
        Actor sistema = Actor.sistema("PRUEBA");
        Usuario admin = usuarios.crear("admin.imp", "Administrador", "clave-segura-1", Rol.ADMINISTRADOR, sistema);
        Usuario responsable = usuarios.crear("resp.imp", "M. Herrera", "clave-segura-2", Rol.RESPONSABLE, sistema);
        Usuario otro = usuarios.crear("resp2.imp", "J. Paredes", "clave-segura-3", Rol.RESPONSABLE, sistema);
        Usuario operador = usuarios.crear("oper.imp", "Operador", "clave-segura-4", Rol.OPERADOR, sistema);
        comoAdmin = new UsuarioAutenticado(admin);
        comoResponsable = new UsuarioAutenticado(responsable);
        comoOperador = new UsuarioAutenticado(operador);

        Integer idEntorno = catalogos.crearEntorno("Producción", null, sistema).getId();
        catalogos.crearCriticidad(new DatosNivelCriticidad("Alta", 1, 7, 30, 24, 48), sistema);
        Integer idMedia = catalogos.crearCriticidad(new DatosNivelCriticidad("Media", 2, 14, 60, 12, 72), sistema).getId();
        var so = catalogos.crearSistemaOperativo("Ubuntu", FamiliaSistemaOperativo.LINUX, sistema);
        Integer idVersion = catalogos.agregarVersion(so.getId(), "22.04", sistema).getVersiones().getFirst().getId();

        Actor actorAdmin = Actor.usuario(admin.getId());
        idExistente = servidores.crear(new DatosServidor("srv-exist-01", "10.30.0.1", "DC-Norte", null, null, null, null,
                idVersion, null, idEntorno, idMedia, responsable.getId(), "Registrado", List.of(), null, null, null), actorAdmin).getId();
        servidores.crear(new DatosServidor("srv-otro-01", "10.30.0.9", null, null, null, null, null,
                idVersion, null, idEntorno, idMedia, otro.getId(), null, List.of(), null, null, null), actorAdmin);
        servidores.crear(new DatosServidor("srv-tercero-01", "10.30.0.5", null, null, null, null, null,
                idVersion, null, idEntorno, idMedia, responsable.getId(), null, List.of(), null, null, null), actorAdmin);
    }

    @AfterEach
    void limpiar() {
        limpiarDatos();
    }

    private static MockMultipartFile archivo(String nombre, String contenido) {
        return new MockMultipartFile("archivo", nombre, "application/octet-stream",
                contenido.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartHttpServletRequestBuilder analisis(MockMultipartFile archivo) {
        return multipart("/api/servidores/importacion/analisis").file(archivo);
    }

    private JsonNode filaDe(JsonNode analisis, int numero) {
        for (JsonNode fila : analisis.get("filas")) {
            if (fila.get("fila").asInt() == numero) {
                return fila;
            }
        }
        throw new AssertionError("No se encontró la fila " + numero);
    }

    private static List<String> textos(JsonNode lista) {
        List<String> textos = new ArrayList<>();
        lista.forEach(n -> textos.add(n.asText()));
        return textos;
    }

    private static final String ARCHIVO_MIXTO = ENCABEZADO
            + "srv-nuevo-01,10.30.0.2,Ubuntu,22.04,Producción,Alta,resp.imp,Nuevo,ACTIVO\n"        // 2: nuevo
            + "srv-exist-01,10.30.0.1,ubuntu,22.04,producción,Alta,RESP.IMP,Cambia criticidad,\n"  // 3: duplicado
            + "srv-nuevo-01,10.30.0.3,Ubuntu,22.04,Producción,Alta,resp.imp,,\n"                    // 4: repetido
            + "srv-malo,999.1.1.1,Ubuntu,24.04,Pruebas,Alta,nadie,,\n"                               // 5: erróneo
            + "srv-otro-01,10.30.0.5,Ubuntu,22.04,Producción,Media,resp2.imp,,\n";                  // 6: conflicto

    @Test
    @DisplayName("HU08 CA2: la vista previa clasifica nuevos, duplicados y erróneos con su motivo, sin escribir")
    void vistaPrevia() throws Exception {
        String cuerpo = mockMvc.perform(analisis(archivo("inventario.csv", ARCHIVO_MIXTO))
                        .with(user(comoAdmin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode analisis = json.readTree(cuerpo);

        assertThat(analisis.get("formato").asText()).isEqualTo("CSV");
        assertThat(analisis.get("nuevos").asInt()).isEqualTo(1);
        assertThat(analisis.get("duplicados").asInt()).isEqualTo(3);
        assertThat(analisis.get("erroneos").asInt()).isEqualTo(1);
        assertThat(textos(analisis.get("columnasIgnoradas"))).containsExactly("Estado");

        assertThat(filaDe(analisis, 2).get("estado").asText()).isEqualTo("NUEVA");

        JsonNode duplicado = filaDe(analisis, 3);
        assertThat(duplicado.get("estado").asText()).isEqualTo("DUPLICADA");
        assertThat(duplicado.get("sobrescribible").asBoolean()).isTrue();
        assertThat(duplicado.get("servidorExistente").get("id").asInt()).isEqualTo(idExistente);
        // Solo cambian criticidad y descripción: el VDC no viene en el archivo y se conserva
        assertThat(textos(duplicado.get("camposModificados"))).containsExactly("Criticidad", "Descripción");

        JsonNode repetido = filaDe(analisis, 4);
        assertThat(repetido.get("sobrescribible").asBoolean()).isFalse();
        assertThat(textos(repetido.get("motivos"))).singleElement().asString().contains("fila 2");

        JsonNode erroneo = filaDe(analisis, 5);
        assertThat(erroneo.get("estado").asText()).isEqualTo("ERRONEA");
        assertThat(String.join(" | ", textos(erroneo.get("motivos"))))
                .contains("999.1.1.1 no es válida")
                .contains("no tiene registrada la versión «24.04». Registrados: 22.04")
                .contains("No existe el entorno «Pruebas»")
                .contains("No existe el usuario «nadie»");

        JsonNode conflicto = filaDe(analisis, 6);
        assertThat(conflicto.get("sobrescribible").asBoolean()).isFalse();
        assertThat(textos(conflicto.get("motivos"))).singleElement().asString()
                .contains("srv-otro-01").contains("srv-tercero-01");

        // Nada se escribió
        assertThat(jdbc.queryForObject("select count(*) from servidor", Integer.class)).isEqualTo(3);
    }

    @Test
    @DisplayName("HU08 CA3 y CA4: importa lo válido y solo sobrescribe los duplicados confirmados")
    void importacion() throws Exception {
        mockMvc.perform(multipart("/api/servidores/importacion").file(archivo("inventario.csv", ARCHIVO_MIXTO))
                        .with(user(comoAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creados").value(1))
                .andExpect(jsonPath("$.actualizados").value(0))
                .andExpect(jsonPath("$.omitidos").value(3))
                .andExpect(jsonPath("$.rechazados").value(1))
                .andExpect(jsonPath("$.filas[?(@.fila == 3)].detalle")
                        .value("No se confirmó sobrescribir el servidor existente"));

        // Sin confirmación el existente no cambió y el nuevo nace pendiente de configuración (HU06 CA3)
        assertThat(jdbc.queryForObject("select n.nombre from servidor s join nivel_criticidad n "
                + "on n.id_nivel_criticidad = s.id_nivel_criticidad where s.id_servidor = ?", String.class, idExistente))
                .isEqualTo("Media");
        assertThat(jdbc.queryForObject("select estado::text from servidor where hostname = 'srv-nuevo-01'",
                String.class)).isEqualTo("PENDIENTE_DE_CONFIGURACION");

        // Segunda pasada confirmando la fila 3: el nuevo ya existe y coincide, así que se omite
        mockMvc.perform(multipart("/api/servidores/importacion").file(archivo("inventario.csv", ARCHIVO_MIXTO))
                        .param("sobrescribir", "3")
                        .with(user(comoAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creados").value(0))
                .andExpect(jsonPath("$.actualizados").value(1))
                .andExpect(jsonPath("$.filas[?(@.fila == 2)].detalle")
                        .value(hasItem(containsString("Sus datos coinciden con los registrados"))));

        assertThat(jdbc.queryForObject("select n.nombre || ' / ' || s.descripcion from servidor s join nivel_criticidad n "
                + "on n.id_nivel_criticidad = s.id_nivel_criticidad where s.id_servidor = ?", String.class, idExistente))
                .isEqualTo("Alta / Cambia criticidad");
        assertThat(jdbc.queryForObject("select vdc from servidor where id_servidor = ?", String.class, idExistente))
                .isEqualTo("DC-Norte");
        assertThat(jdbc.queryForObject("select count(*) from auditoria where operacion = 'IMPORTAR_INVENTARIO'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("HU09: exporta dentro del alcance del rol y solo las columnas elegidas")
    void exportacionConAlcance() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/servidores/exportacion")
                        .param("formato", "JSON")
                        .param("columnas", "hostname", "Dirección IP")
                        .with(user(comoResponsable)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("inventario_servidores_2026-09-28.json")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode registros = json.readTree(cuerpo);

        // El responsable solo ve los suyos (R2.1, tabla 3)
        assertThat(registros).hasSize(2);
        assertThat(registros.get(0).get("hostname").asText()).isEqualTo("srv-exist-01");
        List<String> claves = new ArrayList<>();
        registros.get(0).fieldNames().forEachRemaining(claves::add);
        assertThat(claves).containsExactly("hostname", "direccion_ip");

        mockMvc.perform(get("/api/servidores/exportacion").param("formato", "CSV").with(user(comoOperador)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                        .contains("srv-otro-01").contains("Nombre del responsable"));

        assertThat(jdbc.queryForObject("select count(*) from auditoria where operacion = 'EXPORTAR_INVENTARIO'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    @DisplayName("RF12 y RF13: un XLSX exportado se vuelve a importar sin cambios")
    void exportarEImportarXlsx() throws Exception {
        byte[] xlsx = mockMvc.perform(get("/api/servidores/exportacion").param("formato", "XLSX")
                        .with(user(comoAdmin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        mockMvc.perform(multipart("/api/servidores/importacion/analisis")
                        .file(new MockMultipartFile("archivo", "inventario.xlsx", "application/octet-stream", xlsx))
                        .with(user(comoAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registros").value(3))
                .andExpect(jsonPath("$.duplicados").value(3))
                .andExpect(jsonPath("$.sobrescribibles").value(0))
                .andExpect(jsonPath("$.filas[0].motivos[1]").value("Sus datos coinciden con los registrados"))
                .andExpect(jsonPath("$.columnasIgnoradas").value(hasItems(
                        "Estado", "Nombre del responsable", "Fecha de alta", "Fecha de actualización")));
    }

    @Test
    @DisplayName("DEC-37: importa IP adicionales, VDC y recursos; una IP adicional ya usada marca el duplicado")
    void importaVariasDirecciones() throws Exception {
        String csv = "Hostname;Dirección IP;IPs adicionales;VDC;CPU;RAM (GB);Disco virtual (GB);Sistema operativo;"
                + "Versión;Entorno;Criticidad;Responsable\n"
                + "srv-multi-01;10.30.7.1;10.30.7.2, 10.30.7.3;VDC-Sur;4;16,5;250;Ubuntu;22.04;Producción;Alta;resp.imp\n"
                + "srv-multi-02;10.30.8.1;10.30.0.9;VDC-Sur;;;;Ubuntu;22.04;Producción;Alta;resp.imp\n"
                + "srv-multi-03;10.30.9.1;10.30.7.3;;;;;Ubuntu;22.04;Producción;Alta;resp.imp\n"
                + "srv-multi-04;10.30.9.9;;;0;dieciseis;;Ubuntu;22.04;Producción;Alta;resp.imp\n";

        String cuerpo = mockMvc.perform(analisis(archivo("inventario.csv", csv)).with(user(comoAdmin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode analisis = json.readTree(cuerpo);
        assertThat(filaDe(analisis, 2).get("estado").asText()).isEqualTo("NUEVA");
        // 10.30.0.9 es la IP de srv-otro-01: el registro es un duplicado de ese servidor
        assertThat(textos(filaDe(analisis, 3).get("motivos")).getFirst()).contains("srv-otro-01");
        // 10.30.7.3 ya aparece en la fila 2 del mismo archivo
        assertThat(textos(filaDe(analisis, 4).get("motivos")).getFirst()).contains("10.30.7.3").contains("fila 2");
        assertThat(String.join(" | ", textos(filaDe(analisis, 5).get("motivos"))))
                .contains("«CPU» debe ser un número entero entre 1")
                .contains("«RAM (GB)» debe ser un número");

        mockMvc.perform(multipart("/api/servidores/importacion").file(archivo("inventario.csv", csv))
                        .with(user(comoAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creados").value(1));
        assertThat(jdbc.queryForList("select d.direccion || ':' || d.principal from direccion_ip d join servidor s "
                + "using (id_servidor) where s.hostname = 'srv-multi-01' order by d.direccion", String.class))
                .containsExactly("10.30.7.1:true", "10.30.7.2:false", "10.30.7.3:false");
        assertThat(jdbc.queryForObject("select vdc || ' ' || cantidad_cpu || ' ' || ram_gb || ' ' || hd_virtual_gb "
                + "from servidor where hostname = 'srv-multi-01'", String.class)).isEqualTo("VDC-Sur 4 16.50 250.00");
    }

    @Test
    @DisplayName("solo el administrador importa; un archivo sin columnas obligatorias se rechaza completo")
    void permisosYEstructura() throws Exception {
        mockMvc.perform(analisis(archivo("inventario.csv", ARCHIVO_MIXTO)).with(user(comoOperador)))
                .andExpect(status().isForbidden());
        mockMvc.perform(analisis(archivo("inventario.csv", "Hostname,VLAN\nsrv-01,220\n"))
                        .with(user(comoAdmin)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("Faltan columnas obligatorias: Dirección IP")));
        mockMvc.perform(analisis(archivo("inventario.txt", "hola")).with(user(comoAdmin)))
                .andExpect(status().isUnprocessableEntity());
    }
}
