package com.komainos.credencial;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.komainos.PruebaIntegracion;
import com.komainos.credencial.model.TipoAutenticacion;
import com.komainos.credencial.model.TipoUsuario;
import com.komainos.credencial.service.ServicioCredenciales;
import com.komainos.credencial.service.ServicioCredenciales.DatosCredencial;
import com.komainos.credencial.service.ServicioCredenciales.Secreto;
import com.komainos.inventario.model.DatosServidor;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.NivelCriticidad.DatosNivelCriticidad;
import com.komainos.inventario.service.ServicioCatalogos;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.seguridad.model.AlcanceUsuario;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** No es @Transactional: la importación registra cada fila en su propia transacción */
@DisplayName("Credencial principal en la importación y exportación del inventario (RF12, RF13, DEC-39)")
class ImportacionCredencialesIT extends PruebaIntegracion {

    private static final String CLAVE_ADMIN = "clave-segura-1";
    private static final String ARCHIVO = """
            Hostname,Dirección IP,Sistema operativo,Versión,Entorno,Criticidad,Responsable,Mecanismo de acceso,Tipo de usuario,Usuario de acceso,Contraseña o llave,Contraseña su (root)
            srv-nuevo-01,10.40.0.2,Ubuntu,22.04,Producción,Alta,resp.cred,Contraseña,Genérico,appuser, clave con espacios ,root-1
            srv-exist-01,10.40.0.1,Ubuntu,22.04,Producción,Alta,resp.cred,Contraseña,Administrador,root,clave-nueva,
            srv-win-02,10.40.0.3,Windows,2022,Producción,Alta,resp.cred,,,Administrator,Clave-Win,root
            srv-sin-01,10.40.0.4,Ubuntu,22.04,Producción,Alta,resp.cred,,,,,
            """;

    @Autowired ServicioUsuario usuarios;
    @Autowired ServicioCatalogos catalogos;
    @Autowired ServicioServidor servidores;
    @Autowired ServicioCredenciales credenciales;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    private UsuarioAutenticado comoAdmin;
    private AlcanceUsuario alcanceAdmin;
    private Integer idExistente;

    @BeforeEach
    void preparar() {
        limpiarDatos();
        Actor sistema = Actor.sistema("PRUEBA");
        Usuario admin = usuarios.crear("admin.cred", "Administrador", CLAVE_ADMIN, Rol.ADMINISTRADOR, sistema);
        Usuario responsable = usuarios.crear("resp.cred", "M. Herrera", "clave-segura-2", Rol.RESPONSABLE, sistema);
        comoAdmin = new UsuarioAutenticado(admin);
        alcanceAdmin = new AlcanceUsuario(admin.getId(), Rol.ADMINISTRADOR);

        Integer entorno = catalogos.crearEntorno("Producción", null, sistema).getId();
        Integer criticidad = catalogos.crearCriticidad(new DatosNivelCriticidad("Alta", 1, 7, 30, 24, 48), sistema).getId();
        var ubuntu = catalogos.crearSistemaOperativo("Ubuntu", FamiliaSistemaOperativo.LINUX, sistema);
        Integer version = catalogos.agregarVersion(ubuntu.getId(), "22.04", sistema).getVersiones().getFirst().getId();
        var windows = catalogos.crearSistemaOperativo("Windows", FamiliaSistemaOperativo.WINDOWS, sistema);
        catalogos.agregarVersion(windows.getId(), "2022", sistema);

        idExistente = servidores.crear(new DatosServidor("srv-exist-01", "10.40.0.1", null, null, null, null, null,
                version, null, entorno, criticidad, responsable.getId(), null, List.of(), null, null, null),
                alcanceAdmin.actor()).getId();
        credenciales.registrarDocumental(idExistente, new DatosCredencial("Acceso root", "root", null,
                new Secreto(TipoAutenticacion.PASSWORD, TipoUsuario.ADMINISTRADOR, "clave-vieja", null)), alcanceAdmin);
    }

    @AfterEach
    void limpiar() {
        limpiarDatos();
    }

    @Test
    @DisplayName("HU08: la vista previa detecta la credencial que cambia sin mostrarla y al confirmar crea una versión")
    void importaCredencialPrincipal() throws Exception {
        String cuerpo = mockMvc.perform(multipart("/api/servidores/importacion/analisis").file(archivo("inventario.csv", ARCHIVO))
                        .with(user(comoAdmin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode analisis = json.readTree(cuerpo);

        assertThat(cuerpo).doesNotContain("clave-nueva").doesNotContain("root-1").doesNotContain("clave con espacios");
        assertThat(textos(analisis.get("columnasReconocidas"))).contains("Contraseña o llave", "Contraseña su (root)");
        assertThat(textos(analisis.get("columnasIgnoradas"))).isEmpty();
        assertThat(fila(analisis, 2).get("estado").asText()).isEqualTo("NUEVA");
        JsonNode duplicado = fila(analisis, 3);
        assertThat(duplicado.get("estado").asText()).isEqualTo("DUPLICADA");
        assertThat(textos(duplicado.get("camposModificados"))).containsExactly("Credencial principal");
        assertThat(duplicado.get("sobrescribible").asBoolean()).isTrue();
        JsonNode windows = fila(analisis, 4);
        assertThat(windows.get("estado").asText()).isEqualTo("ERRONEA");
        assertThat(textos(windows.get("motivos")).getFirst()).startsWith("Credencial:").contains("Windows");
        assertThat(fila(analisis, 5).get("estado").asText()).isEqualTo("NUEVA");

        mockMvc.perform(multipart("/api/servidores/importacion").file(archivo("inventario.csv", ARCHIVO))
                        .param("sobrescribir", "3").with(user(comoAdmin)))
                .andExpect(status().isOk());

        Integer idNuevo = jdbc.queryForObject("select id_servidor from servidor where hostname = 'srv-nuevo-01'", Integer.class);
        var nueva = credenciales.documentalesDe(idNuevo, alcanceAdmin).getFirst();
        assertThat(nueva.isPrincipal()).isTrue();
        var revelada = credenciales.revelar(nueva.getId(), CLAVE_ADMIN, alcanceAdmin);
        assertThat(revelada.secreto()).isEqualTo(" clave con espacios ");
        assertThat(revelada.su()).isEqualTo("root-1");
        assertThat(revelada.version().getTipoUsuario()).isEqualTo(TipoUsuario.GENERICO);

        var existente = credenciales.documentalesDe(idExistente, alcanceAdmin);
        assertThat(existente).hasSize(1);
        assertThat(credenciales.revelar(existente.getFirst().getId(), CLAVE_ADMIN, alcanceAdmin))
                .satisfies(r -> {
                    assertThat(r.secreto()).isEqualTo("clave-nueva");
                    assertThat(r.version().getNumeroVersion()).isEqualTo(2);
                });
        assertThat(jdbc.queryForObject("select count(*) from servidor where hostname = 'srv-win-02'", Integer.class)).isZero();
        Integer idSin = jdbc.queryForObject("select id_servidor from servidor where hostname = 'srv-sin-01'", Integer.class);
        assertThat(credenciales.documentalesDe(idSin, alcanceAdmin)).isEmpty();
    }

    @Test
    @DisplayName("RF13: lo exportado con credencial principal se vuelve a importar sin cambios, también una llave en XLSX")
    void idaYVuelta() throws Exception {
        Integer idLlave = servidores.crear(new DatosServidor("srv-llave-01", "10.40.0.9", null, null, null, null, null,
                jdbc.queryForObject("select id_version_sistema_operativo from version_sistema_operativo where version = '22.04'",
                        Integer.class),
                null, jdbc.queryForObject("select id_entorno from entorno", Integer.class),
                jdbc.queryForObject("select id_nivel_criticidad from nivel_criticidad", Integer.class),
                jdbc.queryForObject("select id_usuario from usuario where codigo = 'resp.cred'", Integer.class),
                null, List.of(), null, null, null), alcanceAdmin.actor()).getId();
        credenciales.registrarDocumental(idLlave, new DatosCredencial("Despliegue", "deploy", null,
                new Secreto(TipoAutenticacion.LLAVE_SSH, TipoUsuario.GENERICO, """
                        -----BEGIN OPENSSH PRIVATE KEY-----
                        b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
                        -----END OPENSSH PRIVATE KEY-----
                        """, "=empieza-como-formula")), alcanceAdmin);

        byte[] exportado = mockMvc.perform(post("/api/servidores/exportacion/con-credencial").param("formato", "XLSX")
                        .with(user(comoAdmin)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("contrasena", CLAVE_ADMIN))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        JsonNode analisis = json.readTree(mockMvc.perform(multipart("/api/servidores/importacion/analisis")
                        .file(new MockMultipartFile("archivo", "inventario.xlsx", "application/octet-stream", exportado))
                        .with(user(comoAdmin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertThat(analisis.get("erroneos").asInt()).isZero();
        for (JsonNode fila : analisis.get("filas")) {
            assertThat(textos(fila.get("camposModificados"))).as(fila.get("hostname").asText()).isEmpty();
        }
    }

    private static MockMultipartFile archivo(String nombre, String contenido) {
        return new MockMultipartFile("archivo", nombre, "application/octet-stream", contenido.getBytes(StandardCharsets.UTF_8));
    }

    private static JsonNode fila(JsonNode analisis, int numero) {
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
}
