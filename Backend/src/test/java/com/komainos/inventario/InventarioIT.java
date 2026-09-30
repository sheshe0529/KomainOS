package com.komainos.inventario;

import com.komainos.PruebaIntegracion;
import com.komainos.auditoria.repository.AuditoriaRepositorio;
import com.komainos.inventario.model.CalendarioSemanal.IntervaloSemanal;
import com.komainos.inventario.model.DatosServidor;
import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.EstadoSolicitudBaja;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.inventario.model.NivelCriticidad.DatosNivelCriticidad;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.service.ServicioCatalogos;
import com.komainos.inventario.service.ServicioGrupo;
import com.komainos.inventario.service.ServicioServidor.DatosConfiguracion;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.model.UsuarioAutenticado;
import com.komainos.seguridad.service.ServicioUsuario;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

import static com.komainos.inventario.model.DiaSemana.DOMINGO;
import static com.komainos.inventario.model.DiaSemana.SABADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@DisplayName("Inventario contra PostgreSQL (RF09-RF21, RF72, RF73, RF76)")
class InventarioIT extends PruebaIntegracion {

    @Autowired ServicioCatalogos catalogos;
    @Autowired ServicioUsuario usuarios;
    @Autowired ServicioServidor servidores;
    @Autowired ServicioGrupo grupos;
    @Autowired ConfiguracionServidorRepositorio configuraciones;
    @Autowired AuditoriaRepositorio auditorias;
    @Autowired EntityManager em;
    @Autowired MockMvc mockMvc;

    private final Actor sistema = Actor.sistema("PRUEBA");
    private Usuario admin;
    private Usuario responsable;
    private Usuario otroResponsable;
    private Integer idEntorno;
    private Integer idAlta;
    private Integer idMedia;
    private Integer idVersion;

    @BeforeEach
    void catalogosBase() {
        admin = usuarios.crear("admin.it", "Administrador IT", "clave-segura-1", Rol.ADMINISTRADOR, sistema);
        responsable = usuarios.crear("resp.it", "M. Herrera", "clave-segura-2", Rol.RESPONSABLE, sistema);
        otroResponsable = usuarios.crear("resp2.it", "J. Paredes", "clave-segura-3", Rol.RESPONSABLE, sistema);
        idEntorno = catalogos.crearEntorno("Producción IT", null, sistema).getId();
        idAlta = catalogos.crearCriticidad(new DatosNivelCriticidad("Alta IT", 1, 7, 30, 24, 48), sistema).getId();
        idMedia = catalogos.crearCriticidad(new DatosNivelCriticidad("Media IT", 2, 14, 60, 12, 72), sistema).getId();
        var so = catalogos.crearSistemaOperativo("Ubuntu IT", FamiliaSistemaOperativo.LINUX, sistema);
        idVersion = catalogos.agregarVersion(so.getId(), "22.04", sistema).getVersiones().getFirst().getId();
    }

    private Servidor crearServidor(String hostname, String ip, Integer idCriticidad, Usuario dueno) {
        return servidores.crear(new DatosServidor(hostname, ip, "DC-Norte", "esx-01", "VLAN 220", "cl-app", null,
                idVersion, "VMware", idEntorno, idCriticidad, dueno.getId(), null, List.of(), null, null, null), Actor.usuario(admin.getId()));
    }

    private static IntervaloSemanal ventana(com.komainos.inventario.model.DiaSemana di, String hi,
                                            com.komainos.inventario.model.DiaSemana df, String hf) {
        return new IntervaloSemanal(di, LocalTime.parse(hi), df, LocalTime.parse(hf));
    }

    @Test
    @DisplayName("alta, configuración y ventana: el servidor queda ACTIVO y la ficha trae todo cargado")
    void flujoDeInventario() {
        Servidor s = crearServidor("srv-app-01", "10.20.1.11", idAlta, responsable);
        AlcanceUsuario alcanceAdmin = new AlcanceUsuario(admin.getId(), Rol.ADMINISTRADOR);
        servidores.configurar(s.getId(), new DatosConfiguracion(null, null, ModalidadPlanificacion.BAJO_DEMANDA),
                alcanceAdmin.actor());
        servidores.reemplazarVentanas(s.getId(), List.of(ventana(SABADO, "22:00", DOMINGO, "02:00")), alcanceAdmin);
        em.flush();
        em.clear();

        var ficha = servidores.ficha(s.getId(), alcanceAdmin);

        assertThat(ficha.servidor().getEstado()).isEqualTo(EstadoServidor.ACTIVO);
        assertThat(ficha.configuracion()).get()
                .satisfies(c -> assertThat(c.getFrecuenciaMantenimientoDias()).isEqualTo(30));
        assertThat(ficha.servidor().getVentanas()).hasSize(1);
        // Con open-in-view desactivado, la ficha debe salir con sus asociaciones cargadas.
        assertThat(Hibernate.isInitialized(ficha.servidor().getVentanas())).isTrue();
        assertThat(Hibernate.isInitialized(ficha.servidor().getVersionSistemaOperativo().getSistemaOperativo())).isTrue();
        // El enumerado se guarda con el tipo nativo de PostgreSQL.
        assertThat(jdbc.queryForObject("select estado::text from servidor where id_servidor = ?", String.class,
                s.getId())).isEqualTo("ACTIVO");
        // La hora de la ventana se guarda tal cual, sin desplazamiento de zona (DEC-06).
        assertThat(jdbc.queryForObject("select hora_inicio::text || '-' || hora_fin::text from ventana_mantenimiento "
                + "where id_servidor = ?", String.class, s.getId())).isEqualTo("22:00:00-02:00:00");
    }

    @Test
    @DisplayName("RF21/RF76: el grupo toma la mayor criticidad y la intersección de ventanas; cada integrante una vez")
    void grupoDerivaCriticidadYVentana() {
        AlcanceUsuario alcanceAdmin = new AlcanceUsuario(admin.getId(), Rol.ADMINISTRADOR);
        Servidor a = crearServidor("srv-db-01", "10.20.2.10", idMedia, responsable);
        Servidor b = crearServidor("srv-db-02", "10.20.2.11", idAlta, responsable);
        // Varios intervalos por servidor: la consulta de la ficha repite cada integrante una vez por intervalo.
        servidores.reemplazarVentanas(a.getId(), List.of(ventana(SABADO, "00:00", SABADO, "06:00"),
                ventana(DOMINGO, "01:00", DOMINGO, "03:00"), ventana(DOMINGO, "10:00", DOMINGO, "11:00")), alcanceAdmin);
        servidores.reemplazarVentanas(b.getId(), List.of(ventana(SABADO, "02:00", SABADO, "08:00"),
                ventana(DOMINGO, "00:00", DOMINGO, "04:00")), alcanceAdmin);
        Integer idGrupo = grupos.crear("Grupo DB IT", null, alcanceAdmin.actor()).getId();
        grupos.reemplazarIntegrantes(idGrupo, List.of(a.getId(), b.getId()), alcanceAdmin.actor());
        grupos.configurar(idGrupo, new ServicioGrupo.DatosConfiguracionGrupo(null, null,
                ModalidadPlanificacion.BAJO_DEMANDA, ModoEjecucion.PARALELO), alcanceAdmin.actor());
        em.flush();
        em.clear();

        var ficha = grupos.ficha(idGrupo, alcanceAdmin);

        assertThat(ficha.criticidadEfectiva()).get().satisfies(n -> assertThat(n.getId()).isEqualTo(idAlta));
        assertThat(ficha.ventanaEfectiva()).containsExactly(ventana(SABADO, "02:00", SABADO, "06:00"),
                ventana(DOMINGO, "01:00", DOMINGO, "03:00"));
        assertThat(ficha.grupo().servidores()).extracting(Servidor::getHostname).containsExactly("srv-db-01", "srv-db-02");
        assertThat(ficha.configuracion()).get()
                .satisfies(c -> assertThat(c.getFrecuenciaMantenimientoDias()).isEqualTo(30));
    }

    @Test
    @DisplayName("RF20: un grupo no admite integrantes con distinto responsable")
    void grupoRechazaIntegrantesIncompatibles() {
        Servidor a = crearServidor("srv-web-01", "10.30.1.20", idMedia, responsable);
        Servidor b = crearServidor("srv-web-02", "10.30.1.21", idMedia, otroResponsable);
        Integer idGrupo = grupos.crear("Grupo Web IT", null, sistema).getId();

        assertThatThrownBy(() -> grupos.reemplazarIntegrantes(idGrupo, List.of(a.getId(), b.getId()), sistema))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("mismo responsable");
    }

    @Test
    @DisplayName("RF72/HU13 CA9: la baja elimina la configuración y conserva su historial en auditoría")
    void bajaConservaHistorial() {
        Servidor s = crearServidor("srv-batch-01", "10.40.1.15", idMedia, responsable);
        Actor actor = Actor.usuario(admin.getId());
        servidores.configurar(s.getId(), new DatosConfiguracion(10, 20, ModalidadPlanificacion.AUTOMATICA), actor);
        em.flush();

        var resultado = servidores.solicitarBaja(s.getId(), "Fin de vida útil", actor);
        em.flush();
        em.clear();

        assertThat(resultado.aplicada()).isTrue();
        assertThat(configuraciones.findByServidorId(s.getId())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from configuracion_mantenimiento", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select estado::text from solicitud_baja where id_servidor = ?", String.class,
                s.getId())).isEqualTo(EstadoSolicitudBaja.APLICADA.name());
        assertThat(auditorias.findByEntidadAndIdEntidadOrderByIdAsc("servidor", s.getId()))
                .extracting("operacion").contains("CREAR_SERVIDOR", "APLICAR_BAJA");
        assertThat(jdbc.queryForObject(
                "select count(*) from auditoria where operacion = 'ELIMINAR_CONFIGURACION' and valor_anterior like '%\"frecuenciaMantenimientoDias\":20%'",
                Integer.class)).isEqualTo(1);

        servidores.reactivar(s.getId(), actor);
        em.flush();
        em.clear();
        assertThat(jdbc.queryForObject("select estado::text from servidor where id_servidor = ?", String.class,
                s.getId())).isEqualTo("PENDIENTE_DE_CONFIGURACION");

        // La ficha conserva el motivo, quién pidió la baja y quién reactivó el servidor.
        var ficha = servidores.ficha(s.getId(), new AlcanceUsuario(admin.getId(), Rol.ADMINISTRADOR));
        assertThat(ficha.bajas()).singleElement().satisfies(b -> {
            assertThat(b.getMotivo()).isEqualTo("Fin de vida útil");
            assertThat(Hibernate.isInitialized(b.getSolicitante())).isTrue();
            assertThat(b.getSolicitante().getCodigo()).isEqualTo("admin.it");
        });
        assertThat(ficha.reactivaciones()).singleElement()
                .satisfies(r -> assertThat(r.usuario().getCodigo()).isEqualTo("admin.it"));
    }

    @Test
    @DisplayName("DEC-37: varias IP por servidor con una principal; se busca por cualquiera y ninguna se comparte")
    void variasDireccionesIp() throws Exception {
        UsuarioAutenticado comoAdmin = new UsuarioAutenticado(admin);
        String cuerpo = """
                {"hostname":"srv-multi-01","direccionIp":"10.60.0.1","direccionesIpAdicionales":["10.60.1.1","FE80::A"],
                 "vdc":"VDC-Norte","cantidadCpu":8,"ramGb":32.5,"hdVirtualGb":500,
                 "idVersionSistemaOperativo":%d,"idEntorno":%d,"idNivelCriticidad":%d,"idResponsable":%d}
                """.formatted(idVersion, idEntorno, idMedia, responsable.getId());
        mockMvc.perform(post("/api/servidores").with(user(comoAdmin))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direccionIp").value("10.60.0.1"))
                .andExpect(jsonPath("$.direccionesIp.length()").value(3))
                .andExpect(jsonPath("$.direccionesIp[0].direccion").value("10.60.0.1"))
                .andExpect(jsonPath("$.direccionesIp[0].principal").value(true))
                .andExpect(jsonPath("$.vdc").value("VDC-Norte"))
                .andExpect(jsonPath("$.cantidadCpu").value(8))
                .andExpect(jsonPath("$.ramGb").value(32.5));
        em.flush();
        Integer id = jdbc.queryForObject("select id_servidor from servidor where hostname = 'srv-multi-01'", Integer.class);
        // Las IPv6 se guardan en minúsculas.
        assertThat(jdbc.queryForList("select direccion from direccion_ip where id_servidor = ? and not principal",
                String.class, id)).containsExactlyInAnyOrder("10.60.1.1", "fe80::a");

        // La búsqueda encuentra el servidor por una IP adicional; el listado muestra la principal.
        mockMvc.perform(get("/api/servidores").param("texto", "10.60.1").with(user(comoAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].direccionIp").value("10.60.0.1"))
                .andExpect(jsonPath("$.contenido[0].cantidadDireccionesIp").value(3));

        // Una IP, un servidor: ni como principal ni como adicional de otro.
        String otro = cuerpo.replace("srv-multi-01", "srv-multi-02").replace("\"10.60.0.1\"", "\"10.60.1.1\"")
                .replace("[\"10.60.1.1\",\"FE80::A\"]", "[]");
        mockMvc.perform(post("/api/servidores").with(user(comoAdmin))
                        .contentType(MediaType.APPLICATION_JSON).content(otro))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("srv-multi-01")));

        // Cambiar la principal conserva la dirección como registro (no se borra y vuelve a crear).
        Integer idAdicional = jdbc.queryForObject(
                "select id_direccion_ip from direccion_ip where direccion = '10.60.1.1'", Integer.class);
        String edicion = cuerpo.replace("\"direccionIp\":\"10.60.0.1\"", "\"direccionIp\":\"10.60.1.1\"")
                .replace("[\"10.60.1.1\",\"FE80::A\"]", "[\"10.60.0.1\"]");
        mockMvc.perform(put("/api/servidores/" + id).with(user(comoAdmin))
                        .contentType(MediaType.APPLICATION_JSON).content(edicion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direccionIp").value("10.60.1.1"))
                .andExpect(jsonPath("$.direccionesIp.length()").value(2));
        em.flush();
        assertThat(jdbc.queryForObject("select id_direccion_ip from direccion_ip where principal and id_servidor = ?",
                Integer.class, id)).isEqualTo(idAdicional);
    }

    @Test
    @DisplayName("autorización: el responsable solo ve sus servidores y no puede registrar")
    void autorizacionPorRol() throws Exception {
        crearServidor("srv-propio-01", "10.50.0.1", idMedia, responsable);
        crearServidor("srv-ajeno-01", "10.50.0.2", idMedia, otroResponsable);
        em.flush();
        UsuarioAutenticado comoResponsable = new UsuarioAutenticado(responsable);

        mockMvc.perform(get("/api/servidores").with(user(comoResponsable)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].hostname").value("srv-propio-01"));

        // Cuerpo valido a proposito: la validacion del cuerpo ocurre antes que
        // @PreAuthorize, y con un cuerpo invalido se obtendria 400 en vez de 403.
        String cuerpoValido = """
                {"hostname":"srv-nuevo-01","direccionIp":"10.50.0.9","idVersionSistemaOperativo":%d,
                 "idEntorno":%d,"idNivelCriticidad":%d,"idResponsable":%d}
                """.formatted(idVersion, idEntorno, idMedia, responsable.getId());
        mockMvc.perform(post("/api/servidores").with(user(comoResponsable))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpoValido))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        Usuario operador = usuarios.crear("op.it", "Operador IT", "clave-segura-4", Rol.OPERADOR, sistema);
        mockMvc.perform(put("/api/servidores/1/ventanas").with(user(new UsuarioAutenticado(operador)))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ventanas\":[]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/servidores"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }
}
