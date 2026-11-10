package com.komainos.credencial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.komainos.PruebaIntegracion;
import com.komainos.ejecucion.model.PropositoEjecucion;
import com.komainos.ejecucion.service.PuertoConexionAutomatizacion;
import com.komainos.inventario.model.CalendarioSemanal.IntervaloSemanal;
import com.komainos.inventario.model.DatosServidor;
import com.komainos.inventario.model.DiaSemana;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.NivelCriticidad.DatosNivelCriticidad;
import com.komainos.inventario.service.ServicioCatalogos;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.planificacion.service.ServicioPlanificacion;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.seguridad.service.ServicioUsuario;
import com.komainos.shared.model.Actor;
import jakarta.persistence.EntityManager;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
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
@DisplayName("Credenciales documentales y cuentas de servicio contra PostgreSQL (RF04-RF08, RF13, DEC-38, DEC-39)")
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
    @Autowired ServicioPlanificacion planificacion;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired EntityManager em;
    @Autowired PuertoConexionAutomatizacion conexiones;

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
        crearDocumental(idLinux, "Acceso root", "root", "PASSWORD", "ADMINISTRADOR", "S3creto-root", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fechaSecreto").value("2026-09-28T14:00:00Z"))
                .andExpect(jsonPath("$.tipoAutenticacion").value("PASSWORD"))
                .andExpect(jsonPath("$.tipoUsuario").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$.conSu").value(false))
                .andExpect(jsonPath("$.principal").value(true));

        mockMvc.perform(comoAdmin(get("/api/servidores/{id}/credenciales", idLinux)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Acceso root"))
                .andExpect(jsonPath("$[0].estado").value("VIGENTE"))
                .andExpect(jsonPath("$[0].secreto").doesNotExist())
                .andExpect(jsonPath("$..secretoCifrado").isEmpty());

        Map<String, Object> fila = jdbc.queryForMap(
                "select secreto_cifrado, iv_nonce, tag_autenticacion, algoritmo, su_secreto_cifrado from credencial_secreto");
        assertThat(new String((byte[]) fila.get("secreto_cifrado"), StandardCharsets.UTF_8)).doesNotContain("S3creto");
        assertThat((byte[]) fila.get("iv_nonce")).hasSize(12);
        assertThat((byte[]) fila.get("tag_autenticacion")).hasSize(16);
        assertThat(fila.get("algoritmo")).isEqualTo("AES-256-GCM");
        assertThat(fila.get("su_secreto_cifrado")).isNull();
        assertThat(auditorias()).isNotEmpty().noneMatch(v -> v.contains("S3creto"));
    }

    @Test
    @DisplayName("RF08/DEC-39: usuario Genérico con contraseña su, rotación que conserva la contraseña y revelado de ambas")
    void genericoConSu() throws Exception {
        Integer id = idDe(crearDocumental(idLinux, "Usuario de aplicación", "appuser", "PASSWORD", "GENERICO",
                "clave-app", "root-1"));
        em.flush();
        assertThat(jdbc.queryForObject("select tipo_usuario::text from credencial_secreto", String.class))
                .isEqualTo("GENERICO");
        byte[] ivAnterior = jdbc.queryForObject("select iv_nonce from credencial_secreto", byte[].class);

        // Solo cambia la contraseña su: la del usuario se conserva y el secreto se sobrescribe con un IV nuevo (RNF04)
        mockMvc.perform(comoAdmin(put("/api/credenciales/{id}/secreto", id))
                        .content(cuerpo("tipoAutenticacion", "PASSWORD", "tipoUsuario", "GENERICO", "secretoSu", "root-2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conSu").value(true));
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from credencial_secreto where id_credencial = ?", Integer.class, id))
                .isOne();
        assertThat(jdbc.queryForObject("select iv_nonce from credencial_secreto", byte[].class)).isNotEqualTo(ivAnterior);

        mockMvc.perform(comoAdmin(post("/api/credenciales/{id}/revelado", id)).content(cuerpo("contrasena", "incorrecta")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value("La contraseña no es correcta"));
        assertThat(revelados()).isZero();

        mockMvc.perform(comoAdmin(post("/api/credenciales/{id}/revelado", id)).content(cuerpo("contrasena", CLAVE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.secreto").value("clave-app"))
                .andExpect(jsonPath("$.secretoSu").value("root-2"))
                .andExpect(jsonPath("$.tipoUsuario").value("GENERICO"))
                .andExpect(jsonPath("$.fechaSecreto").exists())
                .andExpect(jsonPath("$.segundosVisible").value(30));
        assertThat(revelados()).isOne();
        assertThat(auditorias()).noneMatch(v -> v.contains("clave-app") || v.contains("root-1") || v.contains("root-2"));

        // Pasar a Administrador quita la contraseña su
        mockMvc.perform(comoAdmin(put("/api/credenciales/{id}/secreto", id))
                        .content(cuerpo("tipoAutenticacion", "PASSWORD", "tipoUsuario", "ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conSu").value(false));
    }

    @Test
    @DisplayName("DEC-39: Linux exige tipo de usuario y su para Genérico, Windows solo usuario y contraseña")
    void reglasPorFamilia() throws Exception {
        crearDocumental(idWindows, "Administrador local", "Administrator", "LLAVE_SSH", null, LLAVE_SSH, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("WinRM")));
        crearDocumental(idWindows, "Usuario", "user", "PASSWORD", "GENERICO", "Clave-Win-1", "root")
                .andExpect(status().isUnprocessableEntity());
        crearDocumental(idWindows, "Administrador local", "Administrator", "PASSWORD", null, "Clave-Win-1", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoUsuario").doesNotExist());

        crearDocumental(idLinux, "Sin tipo", "deploy", "PASSWORD", null, "clave", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("Administrador o Genérico")));
        crearDocumental(idLinux, "Genérico sin su", "deploy", "PASSWORD", "GENERICO", "clave", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("contraseña su")));
        crearDocumental(idLinux, "Admin con su", "deploy", "PASSWORD", "ADMINISTRADOR", "clave", "root")
                .andExpect(status().isUnprocessableEntity());
        crearDocumental(idLinux, "Llave inválida", "deploy", "LLAVE_SSH", "ADMINISTRADOR", "no es una llave", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("PRIVATE KEY")));
        crearDocumental(idLinux, "Despliegue", "deploy", "LLAVE_SSH", "GENERICO", LLAVE_SSH, "root-pass")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipoAutenticacion").value("LLAVE_SSH"))
                .andExpect(jsonPath("$.conSu").value(true));
    }

    @Test
    @DisplayName("DEC-39: la primera es la principal, se puede cambiar y al revocarla pasa a la vigente más antigua")
    void credencialPrincipal() throws Exception {
        Integer primera = idDe(crearDocumental(idLinux, "Primera", "root", "PASSWORD", "ADMINISTRADOR", "a", null));
        Integer segunda = idDe(crearDocumental(idLinux, "Segunda", "appuser", "PASSWORD", "ADMINISTRADOR", "b", null));
        Integer tercera = idDe(crearDocumental(idLinux, "Tercera", "deploy", "PASSWORD", "ADMINISTRADOR", "c", null));
        assertThat(principal()).isEqualTo(primera);

        mockMvc.perform(comoAdmin(post("/api/credenciales/{id}/principal", tercera)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(true));
        assertThat(principal()).isEqualTo(tercera);

        mockMvc.perform(comoAdmin(post("/api/credenciales/{id}/revocacion", tercera)).content(cuerpo("motivo", "Retiro")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(false));
        assertThat(principal()).isEqualTo(primera);
        assertThat(segunda).isNotNull();

        mockMvc.perform(comoAdmin(get("/api/servidores/{id}/credenciales", idLinux)))
                .andExpect(jsonPath("$[0].nombre").value("Primera"))
                .andExpect(jsonPath("$[0].principal").value(true));
    }

    @Test
    @DisplayName("RF13/HU05 CA3: la exportación con credencial principal y la de todas las credenciales exigen reautenticación")
    void exportaciones() throws Exception {
        crearDocumental(idLinux, "Usuario de aplicación", "appuser", "PASSWORD", "GENERICO", "clave-app", "root-1");
        crearDocumental(idLinux, "Despliegue", "deploy", "LLAVE_SSH", "ADMINISTRADOR", LLAVE_SSH, null);
        crearDocumental(idWindows, "Administrador local", "Administrator", "PASSWORD", null, "Clave-Win-1", null);

        String normal = mockMvc.perform(comoAdmin(get("/api/servidores/exportacion").param("formato", "CSV")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(normal).doesNotContain("Contraseña o llave").doesNotContain("clave-app");

        mockMvc.perform(comoAdmin(post("/api/servidores/exportacion/con-credencial").param("formato", "CSV"))
                        .content(cuerpo("contrasena", "incorrecta")))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/servidores/exportacion/con-credencial").param("formato", "CSV")
                        .with(user(new UsuarioAutenticado(operador))).contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("contrasena", "clave-segura-2")))
                .andExpect(status().isForbidden());

        String conCredencial = mockMvc.perform(comoAdmin(post("/api/servidores/exportacion/con-credencial")
                        .param("formato", "CSV")).content(cuerpo("contrasena", CLAVE_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(conCredencial)
                .contains("\"Mecanismo de acceso\",\"Tipo de usuario\",\"Usuario de acceso\",\"Contraseña o llave\",\"Contraseña su (root)\"")
                .contains("Contraseña,Genérico,appuser,clave-app,root-1")
                .contains("Contraseña,,Administrator,Clave-Win-1,")
                .doesNotContain("deploy");

        String todas = mockMvc.perform(comoAdmin(post("/api/credenciales/exportacion").param("formato", "CSV"))
                        .content(cuerpo("contrasena", CLAVE_ADMIN)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(todas).contains("Hostname,\"Dirección IP\",Credencial,Principal")
                .contains("appuser").contains("deploy").contains("BEGIN OPENSSH PRIVATE KEY").contains("Administrator");
        assertThat(jdbc.queryForList("select operacion from auditoria where operacion like 'EXPORTAR%'", String.class))
                .contains("EXPORTAR_INVENTARIO", "EXPORTAR_CREDENCIALES");
        assertThat(auditorias()).noneMatch(v -> v.contains("clave-app") || v.contains("Clave-Win-1"));
    }

    @Test
    @DisplayName("RF04/RF08: operador y responsable no consultan ni revelan credenciales")
    void soloAdministrador() throws Exception {
        Integer id = idDe(crearDocumental(idLinux, "Acceso root", "root", "PASSWORD", "ADMINISTRADOR", "clave", null));

        mockMvc.perform(get("/api/servidores/{id}/credenciales", idLinux).with(user(new UsuarioAutenticado(responsable))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/credenciales/{id}/revelado", id).with(user(new UsuarioAutenticado(operador)))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("contrasena", "clave-segura-2")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/cuentas-servicio").with(user(new UsuarioAutenticado(operador))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RF05-RF07: asignación propia, predeterminada y masiva, y una cuenta en uso no se revoca")
    void cuentasDeServicio() throws Exception {
        Integer svc = idDe(crearCuenta("svc-ansible", "ansible", "PASSWORD", "Clave-Svc-1"));
        Integer llave = idDe(crearCuenta("svc-llave", "ansible", "LLAVE_SSH", LLAVE_SSH));
        crearCuenta("SVC-ANSIBLE", "otro", "PASSWORD", "x").andExpect(status().isConflict());
        mockMvc.perform(comoAdmin(post("/api/cuentas-servicio")).content(cuerpo("nombre", "svc-su", "usuarioAcceso", "a",
                        "tipoAutenticacion", "PASSWORD", "secreto", "x", "secretoSu", "root")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("habilite la elevación")));

        configurar(idLinux, null, llave)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuracion.idCuentaServicioMantenimiento").value(llave))
                .andExpect(jsonPath("$.configuracion.cuentaServicioMantenimiento.nombre").value("svc-llave"));
        configurar(idWindows, llave)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("WinRM")));
        configurar(idWindows, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuracion.usaPredeterminadaMantenimiento").value(true))
                .andExpect(jsonPath("$.configuracion.cuentaServicioMantenimiento").doesNotExist());

        // srv-win-01 usa la predeterminada: una cuenta con llave SSH no puede serlo
        predeterminada(llave).andExpect(status().isUnprocessableEntity());
        predeterminada(svc).andExpect(status().isOk());
        mockMvc.perform(comoAdmin(get("/api/servidores/{id}", idWindows)))
                .andExpect(jsonPath("$.configuracion.cuentaServicioMantenimiento.nombre").value("svc-ansible"));
        mockMvc.perform(comoAdmin(get("/api/parametros-sistema")))
                .andExpect(jsonPath("$.cuentaServicioMantenimientoPredeterminada.nombre").value("svc-ansible"));

        mockMvc.perform(comoAdmin(put("/api/cuentas-servicio/{id}/servidores", svc))
                         .content(json.writeValueAsString(Map.of("idsServidores", List.of(idLinux), "finalidad", "MANTENIMIENTO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asignados").value(1));
        mockMvc.perform(comoAdmin(get("/api/cuentas-servicio")))
                .andExpect(jsonPath("$[?(@.nombre == 'svc-ansible')].servidores").value(1))
                .andExpect(jsonPath("$[?(@.nombre == 'svc-ansible')].predeterminadaMantenimiento").value(true))
                .andExpect(jsonPath("$[?(@.nombre == 'svc-llave')].servidores").value(0))
                .andExpect(jsonPath("$..secreto").isEmpty());

        revocar(svc).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("1 servidor(es)")))
                .andExpect(jsonPath("$.mensaje").value(containsString("predeterminada")));

        predeterminada(null).andExpect(status().isOk());
        mockMvc.perform(comoAdmin(put("/api/cuentas-servicio/{id}/servidores", llave))
                         .content(json.writeValueAsString(Map.of("idsServidores", List.of(idLinux), "finalidad", "MANTENIMIENTO"))))
                .andExpect(status().isOk());
        revocar(svc).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REVOCADA"))
                .andExpect(jsonPath("$.fechaSecreto").doesNotExist());
        // R2.4: al revocar la credencial su secreto se elimina
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from credencial_secreto where id_credencial = ?", Integer.class, svc))
                .isZero();

        configurar(idWindows, svc)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("revocada")));
        mockMvc.perform(comoAdmin(post("/api/credenciales/{id}/revelado", svc)).content(cuerpo("contrasena", CLAVE_ADMIN)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("La cuenta guarda su cifrado y solo eleva para mantenimiento Linux")
    void cuentaConSu() throws Exception {
        mockMvc.perform(comoAdmin(post("/api/cuentas-servicio"))
                        .content(cuerpo("nombre", "svc-sin-clave-su", "usuarioAcceso", "deploy",
                                "tipoAutenticacion", "PASSWORD", "tipoUsuario", "GENERICO", "secreto", "clave-ssh")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("contraseña su")));

        Integer id = idDe(mockMvc.perform(comoAdmin(post("/api/cuentas-servicio"))
                        .content(cuerpo("nombre", "svc-su", "usuarioAcceso", "deploy", "tipoAutenticacion", "PASSWORD",
                                "tipoUsuario", "GENERICO", "secreto", "clave-ssh", "secretoSu", "clave-root")))
                .andExpect(jsonPath("$.conSu").value(true))
                .andExpect(jsonPath("$.secretoSu").doesNotExist()));
        em.flush();
        em.clear();
        assertThat(jdbc.queryForObject("""
                select su_secreto_cifrado <> convert_to(?, 'UTF8') from credencial_secreto where id_credencial = ?
                """, Boolean.class, "clave-root", id)).isTrue();

        for (PropositoEjecucion proposito : PropositoEjecucion.values()) {
            var acceso = conexiones.resolver(id, FamiliaSistemaOperativo.LINUX, proposito);
            assertThat(acceso.usuario()).isEqualTo("deploy");
            assertThat(acceso.secretoSu()).as("elevación para %s", proposito)
                    .isEqualTo(proposito == PropositoEjecucion.TAREA || proposito == PropositoEjecucion.CONECTIVIDAD
                            ? "clave-root" : null);
        }
        assertThat(conexiones.resolver(id, FamiliaSistemaOperativo.WINDOWS, PropositoEjecucion.TAREA).secretoSu()).isNull();

        // Rotar el acceso SSH sin indicar el tipo mantiene la elevación ya configurada.
        mockMvc.perform(comoAdmin(put("/api/credenciales/{id}/secreto", id))
                        .content(cuerpo("tipoAutenticacion", "LLAVE_SSH", "secreto", LLAVE_SSH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conSu").value(true));
        var conLlave = conexiones.resolver(id, FamiliaSistemaOperativo.LINUX, PropositoEjecucion.TAREA);
        assertThat(conLlave.llave()).isTrue();
        assertThat(conLlave.secretoSu()).isEqualTo("clave-root");

        mockMvc.perform(comoAdmin(put("/api/credenciales/{id}/secreto", id))
                        .content(cuerpo("tipoAutenticacion", "LLAVE_SSH", "tipoUsuario", "ADMINISTRADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conSu").value(false));
        em.flush();
        assertThat(jdbc.queryForObject("select su_secreto_cifrado from credencial_secreto where id_credencial = ?",
                byte[].class, id)).isNull();
        assertThat(auditorias()).noneMatch(v -> v.contains("clave-root") || v.contains("clave-ssh"));
    }

    @Test
    @DisplayName("RF05-RF07: cada finalidad conserva su cuenta propia, su predeterminada y su asignación masiva")
    void cuentasIndependientesPorFinalidad() throws Exception {
        Integer evaluacion = idDe(crearCuenta("svc-evaluacion", "lector", "PASSWORD", "Clave-Evaluacion-1"));
        Integer mantenimiento = idDe(crearCuenta("svc-mantenimiento", "administrador", "PASSWORD", "Clave-Mantenimiento-1"));
        Integer otraEvaluacion = idDe(crearCuenta("svc-evaluacion-nueva", "lector2", "PASSWORD", "Clave-Evaluacion-2"));

        configurar(idLinux, evaluacion, mantenimiento)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuracion.cuentaServicioEvaluacion.nombre").value("svc-evaluacion"))
                .andExpect(jsonPath("$.configuracion.cuentaServicioMantenimiento.nombre").value("svc-mantenimiento"))
                .andExpect(jsonPath("$.configuracion.usaPredeterminadaEvaluacion").value(false))
                .andExpect(jsonPath("$.configuracion.usaPredeterminadaMantenimiento").value(false));

        predeterminada(evaluacion, "EVALUACION").andExpect(status().isOk());
        predeterminada(mantenimiento, "MANTENIMIENTO").andExpect(status().isOk());
        configurar(idWindows, null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuracion.cuentaServicioEvaluacion.nombre").value("svc-evaluacion"))
                .andExpect(jsonPath("$.configuracion.cuentaServicioMantenimiento.nombre").value("svc-mantenimiento"))
                .andExpect(jsonPath("$.configuracion.usaPredeterminadaEvaluacion").value(true))
                .andExpect(jsonPath("$.configuracion.usaPredeterminadaMantenimiento").value(true));
        mockMvc.perform(comoAdmin(get("/api/parametros-sistema")))
                .andExpect(jsonPath("$.cuentaServicioEvaluacionPredeterminada.id").value(evaluacion))
                .andExpect(jsonPath("$.cuentaServicioMantenimientoPredeterminada.id").value(mantenimiento));

        mockMvc.perform(comoAdmin(put("/api/cuentas-servicio/{id}/servidores", otraEvaluacion))
                        .content(json.writeValueAsString(Map.of("idsServidores", List.of(idLinux), "finalidad", "EVALUACION"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asignados").value(1));
        mockMvc.perform(comoAdmin(get("/api/servidores/{id}", idLinux)))
                .andExpect(jsonPath("$.configuracion.idCuentaServicioEvaluacion").value(otraEvaluacion))
                .andExpect(jsonPath("$.configuracion.idCuentaServicioMantenimiento").value(mantenimiento));
        mockMvc.perform(comoAdmin(get("/api/cuentas-servicio")))
                .andExpect(jsonPath("$[?(@.nombre == 'svc-evaluacion')].predeterminadaEvaluacion").value(true))
                .andExpect(jsonPath("$[?(@.nombre == 'svc-evaluacion')].predeterminadaMantenimiento").value(false))
                .andExpect(jsonPath("$[?(@.nombre == 'svc-mantenimiento')].predeterminadaMantenimiento").value(true));
        revocar(otraEvaluacion).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("1 servidor(es)")));
    }

    @Test
    @DisplayName("CP208: una cuenta que usará una orden pendiente no se revoca hasta que esa orden termine o se cancele")
    void cuentaConOrdenesPendientes() throws Exception {
        Integer svc = idDe(crearCuenta("svc-ansible", "ansible", "PASSWORD", "Clave-Svc-1"));
        Integer otra = idDe(crearCuenta("svc-reemplazo", "ansible", "PASSWORD", "Clave-Svc-2"));
        configurar(idLinux, svc).andExpect(status().isOk());
        servidores.reemplazarVentanas(idLinux, List.of(new IntervaloSemanal(DiaSemana.SABADO, LocalTime.of(1, 0),
                DiaSemana.SABADO, LocalTime.of(9, 0))), new AlcanceUsuario(admin.getId(), Rol.ADMINISTRADOR));
        Instant inicio = LocalDateTime.parse("2026-10-03T01:00").atZone(ZoneId.of("America/Lima")).toInstant();
        Orden orden = planificacion.programar(idLinux, null, inicio, "Parche", Actor.usuario(admin.getId()));
        // El servidor ya usa otra cuenta, pero la orden programada conserva la que tenía al planificarse
        configurar(idLinux, otra).andExpect(status().isOk());

        revocar(svc).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("1 orden(es) pendiente(s)")));

        planificacion.cancelar(orden.getId(), "Servicio migrado", Actor.usuario(admin.getId()));
        revocar(svc).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REVOCADA"));
    }

    @Test
    @DisplayName("la cuenta heredada se valida aunque la configuración no elija una: la predeterminada debe servir")
    void cuentaEfectivaHeredada() throws Exception {
        Integer llave = idDe(crearCuenta("svc-llave", "ansible", "LLAVE_SSH", LLAVE_SSH));
        configurar(idLinux, null).andExpect(status().isOk());
        predeterminada(llave).andExpect(status().isOk());

        // Windows no admite una llave SSH: heredar la predeterminada se rechaza igual que asignarla
        configurar(idWindows, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(containsString("WinRM")));
    }

    @Test
    @DisplayName("RF07: la asignación masiva exige que cada servidor tenga configuración de mantenimiento")
    void asignacionMasivaSinConfiguracion() throws Exception {
        Integer svc = idDe(crearCuenta("svc-ansible", "ansible", "PASSWORD", "Clave-Svc-1"));

        mockMvc.perform(comoAdmin(put("/api/cuentas-servicio/{id}/servidores", svc))
                         .content(json.writeValueAsString(Map.of("idsServidores", List.of(idLinux), "finalidad", "MANTENIMIENTO"))))
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

    private ResultActions crearDocumental(Integer idServidor, String nombre, String usuario, String tipo,
                                          String tipoUsuario, String secreto, String su) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("usuarioAcceso", usuario);
        cuerpo.put("tipoAutenticacion", tipo);
        cuerpo.put("tipoUsuario", tipoUsuario);
        cuerpo.put("secreto", secreto);
        cuerpo.put("secretoSu", su);
        return mockMvc.perform(comoAdmin(post("/api/servidores/{id}/credenciales", idServidor))
                .content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions crearCuenta(String nombre, String usuario, String tipo, String secreto) throws Exception {
        return mockMvc.perform(comoAdmin(post("/api/cuentas-servicio"))
                .content(cuerpo("nombre", nombre, "usuarioAcceso", usuario, "tipoAutenticacion", tipo, "secreto", secreto)));
    }

    private ResultActions configurar(Integer idServidor, Integer idCuenta) throws Exception {
        return configurar(idServidor, idCuenta, idCuenta);
    }

    private ResultActions configurar(Integer idServidor, Integer idEvaluacion, Integer idMantenimiento) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("modalidadPlanificacion", "BAJO_DEMANDA");
        cuerpo.put("idCuentaServicioEvaluacion", idEvaluacion);
        cuerpo.put("idCuentaServicioMantenimiento", idMantenimiento);
        return mockMvc.perform(comoAdmin(put("/api/servidores/{id}/configuracion", idServidor))
                .content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions predeterminada(Integer idCuenta) throws Exception {
        return predeterminada(idCuenta, "MANTENIMIENTO");
    }

    private ResultActions predeterminada(Integer idCuenta, String finalidad) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("idCuentaServicio", idCuenta);
        cuerpo.put("finalidad", finalidad);
        return mockMvc.perform(comoAdmin(put("/api/cuentas-servicio/predeterminada")).content(json.writeValueAsString(cuerpo)));
    }

    private ResultActions revocar(Integer id) throws Exception {
        return mockMvc.perform(comoAdmin(post("/api/credenciales/{id}/revocacion", id)).content(cuerpo("motivo", "Retiro")));
    }

    private Integer idDe(ResultActions resultado) throws Exception {
        return JsonPath.read(resultado.andReturn().getResponse().getContentAsString(), "$.id");
    }

    private Integer principal() {
        em.flush();
        return jdbc.queryForObject("select id_credencial from credencial_documental where id_servidor = ? and principal",
                Integer.class, idLinux);
    }

    private int revelados() {
        return jdbc.queryForObject("select count(*) from auditoria where operacion = 'REVELAR_CREDENCIAL'", Integer.class);
    }

    private List<String> auditorias() {
        return jdbc.queryForList("select coalesce(valor_anterior, '') || coalesce(valor_nuevo, '') from auditoria",
                String.class);
    }

    private String cuerpo(String... claveValor) throws Exception {
        Map<String, String> mapa = new HashMap<>();
        for (int i = 0; i < claveValor.length; i += 2) {
            mapa.put(claveValor[i], claveValor[i + 1]);
        }
        return json.writeValueAsString(mapa);
    }
}
