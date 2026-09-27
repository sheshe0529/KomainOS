package com.komainos.inventario;

import com.komainos.PruebaIntegracion;
import com.komainos.auditoria.infra.AuditoriaRepositorio;
import com.komainos.inventario.dominio.DatosServidor;
import com.komainos.inventario.dominio.EstadoServidor;
import com.komainos.inventario.dominio.EstadoSolicitudBaja;
import com.komainos.inventario.dominio.FamiliaSistemaOperativo;
import com.komainos.inventario.dominio.ModalidadPlanificacion;
import com.komainos.inventario.dominio.ModoEjecucion;
import com.komainos.inventario.dominio.NivelCriticidad.DatosNivelCriticidad;
import com.komainos.inventario.dominio.Servidor;
import com.komainos.inventario.dominio.ServicioCatalogos;
import com.komainos.inventario.dominio.ServicioGrupo;
import com.komainos.inventario.dominio.ServicioServidor;
import com.komainos.inventario.dominio.ServicioServidor.DatosConfiguracion;
import com.komainos.inventario.dominio.ventana.CalendarioSemanal.IntervaloSemanal;
import com.komainos.inventario.infra.ConfiguracionServidorRepositorio;
import com.komainos.seguridad.dominio.AlcanceUsuario;
import com.komainos.seguridad.dominio.Rol;
import com.komainos.seguridad.dominio.ServicioUsuario;
import com.komainos.seguridad.dominio.Usuario;
import com.komainos.seguridad.dominio.UsuarioAutenticado;
import com.komainos.shared.dominio.Actor;
import com.komainos.shared.error.ReglaNegocioException;
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

import static com.komainos.inventario.dominio.DiaSemana.DOMINGO;
import static com.komainos.inventario.dominio.DiaSemana.SABADO;
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
                idVersion, "VMware", idEntorno, idCriticidad, dueno.getId(), null), Actor.usuario(admin.getId()));
    }

    private static IntervaloSemanal ventana(com.komainos.inventario.dominio.DiaSemana di, String hi,
                                            com.komainos.inventario.dominio.DiaSemana df, String hf) {
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
    @DisplayName("RF21/RF76: el grupo toma la mayor criticidad y la intersección de ventanas")
    void grupoDerivaCriticidadYVentana() {
        AlcanceUsuario alcanceAdmin = new AlcanceUsuario(admin.getId(), Rol.ADMINISTRADOR);
        Servidor a = crearServidor("srv-db-01", "10.20.2.10", idMedia, responsable);
        Servidor b = crearServidor("srv-db-02", "10.20.2.11", idAlta, responsable);
        servidores.reemplazarVentanas(a.getId(), List.of(ventana(SABADO, "00:00", SABADO, "06:00")), alcanceAdmin);
        servidores.reemplazarVentanas(b.getId(), List.of(ventana(SABADO, "02:00", SABADO, "08:00")), alcanceAdmin);
        Integer idGrupo = grupos.crear("Grupo DB IT", null, alcanceAdmin.actor()).getId();
        grupos.reemplazarIntegrantes(idGrupo, List.of(a.getId(), b.getId()), alcanceAdmin.actor());
        grupos.configurar(idGrupo, new ServicioGrupo.DatosConfiguracionGrupo(null, null,
                ModalidadPlanificacion.BAJO_DEMANDA, ModoEjecucion.PARALELO), alcanceAdmin.actor());
        em.flush();
        em.clear();

        var ficha = grupos.ficha(idGrupo, alcanceAdmin);

        assertThat(ficha.criticidadEfectiva()).get().satisfies(n -> assertThat(n.getId()).isEqualTo(idAlta));
        assertThat(ficha.ventanaEfectiva()).containsExactly(ventana(SABADO, "02:00", SABADO, "06:00"));
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
        assertThat(jdbc.queryForObject("select estado::text from servidor where id_servidor = ?", String.class,
                s.getId())).isEqualTo("PENDIENTE_DE_CONFIGURACION");
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
