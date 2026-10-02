package com.komainos.credencial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@DisplayName("Credenciales documentales y cuentas de servicio contra PostgreSQL (RF04-RF08)")
class CredencialesIT extends PruebaIntegracion {

    private static final String CLAVE_ADMIN = "clave-segura-1";
    private static final String LLAVE_SSH = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
            -----END OPENSSH PRIVATE KEY-----
            """;

    @Autowired ServicioCatalogos catalogos;
    @Autowired ServicioUsuario usuarios;
    @Autowired ServicioServidor servidores;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;

    private Usuario admin;
    private Usuario operador;
    private Usuario responsable;
    private Integer idLinux;
    private Integer idWindows;

    @BeforeEach
    void inventarioBase() {
        Actor sistema = Actor.sistema("PRUEBA");
        admin = usuarios.crear("admin.cred", "Administrador IT", CLAVE_ADMIN, Rol.ADMINISTRADOR, sistema);
        operador = usuarios.crear("oper.cred", "Operador IT", "clave-segura-2", Rol.OPERADOR, sistema);
        responsable = usuarios.crear("resp.cred", "Responsable IT", "clave-segura-3", Rol.RESPONSABLE, sistema);
        Integer entorno = catalogos.crearEntorno("Producción IT", null, sistema).getId();
        Integer criticidad = catalogos.crearCriticidad(new DatosNivelCriticidad("Alta IT", 1, 7, 30, 24, 48), sistema).getId();
        Integer ubuntu = version(catalogos.crearSistemaOperativo("Ubuntu IT", FamiliaSistemaOperativo.LINUX, sistema).getId(), "22.04");
        Integer windows = version(catalogos.crearSistemaOperativo("Windows IT", FamiliaSistemaOperativo.WINDOWS, sistema).getId(), "2022");
        idLinux = servidor("srv-app-01", "10.70.0.1", ubuntu, entorno, criticidad);
        idWindows = servidor("srv-win-01", "10.70.0.2", windows, entorno, criticidad);
    }

    @Test
    @DisplayName("RF04/RNF11: la credencial documental se guarda cifrada y ninguna respuesta ni auditoría lleva el secreto")
    void documentalSinSecretos() throws Exception {
        crearDocumental(idLinux, "Acceso root", "root", "PASSWORD", "S3creto-root")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroVersion").value(1))
                .andExpect(jsonPath("$.tipoAutenticacion").value("PASSWORD"));

        mockMvc.perform(comoAdmin(get("/api/servidores/{id}/credenciales", idLinux)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Acceso root"))
                .andExpect(jsonPath("$[0].usuarioAcceso").value("root"))
                .andExpect(jsonPath("$[0].estado").value("VIGENTE"))
                .andExpect(jsonPath("$[0].secreto").doesNotExist())
                .andExpect(jsonPath("$..secretoCifrado").isEmpty());

        Map<String, Object> fila = jdbc.queryForMap(
                "select secreto_cifrado, iv_nonce, tag_autenticacion, algoritmo from credencial_version");
        assertThat(new String((byte[]) fila.get("secreto_cifrado"), StandardCharsets.UTF_8)).doesNotContain("S3creto");
        assertThat((byte[]) fila.get("iv_nonce")).hasSize(12);
        assertThat((byte[]) fila.get("tag_autenticacion")).hasSize(16);
        assertThat(fila.get("algoritmo")).isEqualTo("AES-256-GCM");
        assertThat(jdbc.queryForList("select coalesce(valor_nuevo, '') from auditoria", String.class))
                .isNotEmpty().noneMatch(v -> v.contains("S3creto"));
    }

    @Test
    @DisplayName("RF08: el revelado exige reautenticación, muestra la versión vigente y queda auditado sin el secreto")
    void versionYRevelado() throws Exception {
        Integer id = idDe(crearDocumental(idLinux, "Acceso root", "root", "PASSWORD", "primera-clave"));
        mockMvc.perform(comoAdmin(post("/api/credenciales/{id}/versiones", id))
                        .content(cuerpo("tipoAutenticacion", "PASSWORD", "secreto", "segunda-clave")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroVersion").value(2));

        mockMvc.perform(comoAdmin(post("/api/credenciales/{id}/revelado", id)).content(cuerpo("contrasena", "incorrecta")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value("La contraseña no es correcta"));
        assertThat(revelados()).isZero();

        mockMvc.perform(comoAdmin(post("/api/credenciales/{id}/revelado", id)).content(cuerpo("contrasena", CLAVE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.secreto").value("segunda-clave"))
                .andExpect(jsonPath("$.numeroVersion").value(2))
                .andExpect(jsonPath("$.segundosVisible").value(30));
        assertThat(revelados()).isOne();
        assertThat(jdbc.queryForObject("select count(*) from credencial_version", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("select coalesce(valor_nuevo, '') from auditoria", String.class))
                .noneMatch(v -> v.contains("segunda-clave") || v.contains("primera-clave"));
    }

    @Test
    @DisplayName("RF04/RF08: operador y responsable no consultan ni revelan credenciales")
    void soloAdministrador() throws Exception {
        Integer id = idDe(crearDocumental(idLinux, "Acceso root", "root", "PASSWORD", "clave"));

        mockMvc.perform(get("/api/servidores/{id}/credenciales", idLinux).with(user(new UsuarioAutenticado(responsable))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/credenciales/{id}/revelado", id).with(user(new UsuarioAutenticado(operador)))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("contrasena", "clave-segura-2")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/cuentas-servicio").with(user(new UsuarioAutenticado(operador))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("HU04 CA3: un servidor Windows solo admite contraseña y la llave SSH debe ser una llave privada")
    void mecanismoSegunFamilia() throws Exception {
        crearDocumental(idWindows, "Administrador local", "Administrator", "LLAVE_SSH", LLAVE_SSH)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("WinRM")));
        crearDocumental(idWindows, "Administrador local", "Administrator", "PASSWORD", "Clave-Win-1")
                .andExpect(status().isCreated());
        crearDocumental(idLinux, "Despliegue", "deploy", "LLAVE_SSH", "no es una llave")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("PRIVATE KEY")));
        crearDocumental(idLinux, "Despliegue", "deploy", "LLAVE_SSH", LLAVE_SSH)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoAutenticacion").value("LLAVE_SSH"));
    }

    @Test
    @DisplayName("RF05-RF07: asignación propia, predeterminada y masiva, y una cuenta en uso no se revoca")
    void cuentasDeServicio() throws Exception {
        Integer svc = idDe(crearCuenta("svc-ansible", "ansible", "PASSWORD", "Clave-Svc-1"));
        Integer llave = idDe(crearCuenta("svc-llave", "ansible", "LLAVE_SSH", LLAVE_SSH));
        crearCuenta("SVC-ANSIBLE", "otro", "PASSWORD", "x").andExpect(status().isConflict());

        configurar(idLinux, llave)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuracion.idCuentaServicio").value(llave))
                .andExpect(jsonPath("$.configuracion.cuentaServicio.nombre").value("svc-llave"));
        configurar(idWindows, llave)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("WinRM")));
        configurar(idWindows, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuracion.usaCuentaPredeterminada").value(true))
                .andExpect(jsonPath("$.configuracion.cuentaServicio").doesNotExist());

        // srv-win-01 usa la predeterminada: una cuenta con llave SSH no puede serlo
        predeterminada(llave).andExpect(status().isUnprocessableEntity());
        predeterminada(svc).andExpect(status().isOk());
        mockMvc.perform(comoAdmin(get("/api/servidores/{id}", idWindows)))
                .andExpect(jsonPath("$.configuracion.cuentaServicio.nombre").value("svc-ansible"));
        mockMvc.perform(comoAdmin(get("/api/parametros-sistema")))
                .andExpect(jsonPath("$.cuentaServicioPredeterminada.nombre").value("svc-ansible"));

        mockMvc.perform(comoAdmin(put("/api/cuentas-servicio/{id}/servidores", svc))
                        .content(json.writeValueAsString(Map.of("idsServidores", List.of(idLinux)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asignados").value(1));
        mockMvc.perform(comoAdmin(get("/api/cuentas-servicio")))
                .andExpect(jsonPath("$[?(@.nombre == 'svc-ansible')].servidores").value(1))
                .andExpect(jsonPath("$[?(@.nombre == 'svc-ansible')].predeterminada").value(true))
                .andExpect(jsonPath("$[?(@.nombre == 'svc-llave')].servidores").value(0))
                .andExpect(jsonPath("$..secreto").isEmpty());

        revocar(svc).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("1 servidor(es)")))
                .andExpect(jsonPath("$.mensaje").value(containsString("predeterminada")));

        predeterminada(null).andExpect(status().isOk());
        mockMvc.perform(comoAdmin(put("/api/cuentas-servicio/{id}/servidores", llave))
                        .content(json.writeValueAsString(Map.of("idsServidores", List.of(idLinux)))))
                .andExpect(status().isOk());
        revocar(svc).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REVOCADA"));

        configurar(idWindows, svc)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("revocada")));
        mockMvc.perform(comoAdmin(post("/api/credenciales/{id}/revelado", svc)).content(cuerpo("contrasena", CLAVE_ADMIN)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("RF07: la asignación masiva exige que cada servidor tenga configuración de mantenimiento")
    void asignacionMasivaSinConfiguracion() throws Exception {
        Integer svc = idDe(crearCuenta("svc-ansible", "ansible", "PASSWORD", "Clave-Svc-1"));

        mockMvc.perform(comoAdmin(put("/api/cuentas-servicio/{id}/servidores", svc))
                        .content(json.writeValueAsString(Map.of("idsServidores", List.of(idLinux)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("srv-app-01 no tiene configuración")));
    }

    private Integer version(Integer idSistema, String version) {
        return catalogos.agregarVersion(idSistema, version, Actor.sistema("PRUEBA")).getVersiones().getFirst().getId();
    }

    private Integer servidor(String hostname, String ip, Integer idVersion, Integer idEntorno, Integer idCriticidad) {
        return servidores.crear(new DatosServidor(hostname, ip, "VDC-Norte", null, null, null, null, idVersion, null,
                idEntorno, idCriticidad, responsable.getId(), null, List.of(), null, null, null),
                Actor.usuario(admin.getId())).getId();
    }

    private MockHttpServletRequestBuilder comoAdmin(MockHttpServletRequestBuilder peticion) {
        return peticion.with(user(new UsuarioAutenticado(admin))).contentType(MediaType.APPLICATION_JSON);
    }

    private ResultActions crearDocumental(Integer idServidor, String nombre, String usuario, String tipo, String secreto)
            throws Exception {
        return mockMvc.perform(comoAdmin(post("/api/servidores/{id}/credenciales", idServidor))
                .content(cuerpo("nombre", nombre, "usuarioAcceso", usuario, "tipoAutenticacion", tipo, "secreto", secreto)));
    }

    private ResultActions crearCuenta(String nombre, String usuario, String tipo, String secreto) throws Exception {
        return mockMvc.perform(comoAdmin(post("/api/cuentas-servicio"))
                .content(cuerpo("nombre", nombre, "usuarioAcceso", usuario, "tipoAutenticacion", tipo, "secreto", secreto)));
    }

    private ResultActions configurar(Integer idServidor, Integer idCuenta) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("modalidadPlanificacion", "BAJO_DEMANDA");
        cuerpo.put("idCuentaServicio", idCuenta);
        return mockMvc.perform(comoAdmin(put("/api/servidores/{id}/configuracion", idServidor))
                .content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions predeterminada(Integer idCuenta) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idCuentaServicio", idCuenta);
        return mockMvc.perform(comoAdmin(put("/api/cuentas-servicio/predeterminada")).content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions revocar(Integer id) throws Exception {
        return mockMvc.perform(comoAdmin(post("/api/credenciales/{id}/revocacion", id)).content(cuerpo("motivo", "Retiro")));
    }

    private Integer idDe(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private int revelados() {
        return jdbc.queryForObject("select count(*) from auditoria where operacion = 'REVELAR_CREDENCIAL'", Integer.class);
    }

    private String cuerpo(String... claveValor) throws Exception {
        Map<String, String> mapa = new HashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            mapa.put(claveValor[i], claveValor[i + 1]);
        }
        return json.writeValueAsString(mapa);
    }
}
