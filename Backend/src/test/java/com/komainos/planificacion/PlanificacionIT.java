package com.komainos.planificacion;

import com.komainos.PruebaIntegracion;
import com.komainos.inventario.model.CalendarioSemanal.IntervaloSemanal;
import com.komainos.inventario.model.DatosServidor;
import com.komainos.inventario.model.DiaSemana;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.inventario.model.NivelCriticidad.DatosNivelCriticidad;
import com.komainos.inventario.service.ServicioCatalogos;
import com.komainos.inventario.service.ServicioGrupo;
import com.komainos.inventario.service.ServicioParametrosSistema.DatosParametros;
import com.komainos.inventario.service.ServicioParametrosSistema;
import com.komainos.inventario.service.ServicioServidor.DatosConfiguracion;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrigenOrden;
import com.komainos.mantenimiento.service.ServicioConsultaOrdenes;
import com.komainos.planificacion.service.ServicioCronograma;
import com.komainos.planificacion.service.ServicioPlanificacion;
import com.komainos.planificacion.service.algoritmo.PlanificacionImposibleException;
import com.komainos.seguridad.model.AlcanceUsuario;
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

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static com.komainos.inventario.model.DiaSemana.DOMINGO;
import static com.komainos.inventario.model.DiaSemana.SABADO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Planificacion contra PostgreSQL (RF27-RF30, RF32, RF46, RF51, RF72).
 *
 * <p>No es @Transactional a proposito: la planificacion automatica y la que se
 * dispara al confirmar una configuracion usan transacciones propias, y sus
 * efectos solo son visibles si se confirman. Cada prueba parte de datos limpios.
 */
@DisplayName("Planificación del mantenimiento contra PostgreSQL")
class PlanificacionIT extends PruebaIntegracion {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Autowired ServicioUsuario usuarios;
    @Autowired ServicioCatalogos catalogos;
    @Autowired ServicioServidor servidores;
    @Autowired ServicioGrupo grupos;
    @Autowired ServicioParametrosSistema parametros;
    @Autowired ServicioPlanificacion planificacion;
    @Autowired ServicioConsultaOrdenes consulta;
    @Autowired ServicioCronograma cronograma;
    @Autowired MockMvc mockMvc;

    private Usuario admin;
    private Actor actorAdmin;
    private AlcanceUsuario alcanceAdmin;
    private Usuario responsable;
    private Integer idEntorno;
    private Integer idAlta;
    private Integer idMedia;
    private Integer idVersion;

    private static Instant lima(String fechaHora) {
        return LocalDateTime.parse(fechaHora).atZone(LIMA).toInstant();
    }

    private static IntervaloSemanal ventana(DiaSemana di, String hi, DiaSemana df, String hf) {
        return new IntervaloSemanal(di, LocalTime.parse(hi), df, LocalTime.parse(hf));
    }

    @BeforeEach
    void preparar() {
        limpiarDatos();
        Actor sistema = Actor.sistema("PRUEBA");
        admin = usuarios.crear("admin.plan", "Administrador", "clave-segura-1", Rol.ADMINISTRADOR, sistema);
        actorAdmin = Actor.usuario(admin.getId());
        alcanceAdmin = new AlcanceUsuario(admin.getId(), Rol.ADMINISTRADOR);
        responsable = usuarios.crear("resp.plan", "M. Herrera", "clave-segura-2", Rol.RESPONSABLE, sistema);
        idEntorno = catalogos.crearEntorno("Producción", null, sistema).getId();
        idAlta = catalogos.crearCriticidad(new DatosNivelCriticidad("Alta", 1, 7, 30, 24, 24), sistema).getId();
        idMedia = catalogos.crearCriticidad(new DatosNivelCriticidad("Media", 2, 14, 30, 24, 48), sistema).getId();
        var so = catalogos.crearSistemaOperativo("Ubuntu", FamiliaSistemaOperativo.LINUX, sistema);
        idVersion = catalogos.agregarVersion(so.getId(), "22.04", sistema).getVersiones().getFirst().getId();
    }

    private Integer servidor(String hostname, String ip, Integer idCriticidad, Usuario dueno) {
        return servidores.crear(new DatosServidor(hostname, ip, "DC-Norte", null, null, null, null, idVersion, null,
                idEntorno, idCriticidad, dueno.getId(), null), actorAdmin).getId();
    }

    private void ventanas(Integer idServidor, IntervaloSemanal... intervalos) {
        servidores.reemplazarVentanas(idServidor, List.of(intervalos), alcanceAdmin);
    }

    private void configurar(Integer idServidor, ModalidadPlanificacion modalidad) {
        servidores.configurar(idServidor, new DatosConfiguracion(1, 1, modalidad), actorAdmin);
    }

    private List<Orden> ordenesDe(Integer idServidor) {
        return consulta.listar(new com.komainos.mantenimiento.model.FiltroOrdenes(null, idServidor, null, null,
                null, null, null), alcanceAdmin, org.springframework.data.domain.PageRequest.of(0, 50)).getContent();
    }

    private void capacidad(int maximo) {
        parametros.actualizar(new DatosParametros(maximo, 60, 240, 5, 60), actorAdmin);
    }

    @Test
    @DisplayName("RF27/RF28: el proceso automático planifica primero al más crítico y respeta la capacidad")
    void cicloAutomaticoPorCriticidad() {
        capacidad(1);
        Integer media = servidor("srv-media", "10.0.0.1", idMedia, responsable);
        Integer alta = servidor("srv-alta", "10.0.0.2", idAlta, responsable);
        // Sin ventana todavia: configurarlos no genera ordenes (HU13 CA3).
        configurar(media, ModalidadPlanificacion.AUTOMATICA);
        configurar(alta, ModalidadPlanificacion.AUTOMATICA);
        assertThat(ordenesDe(media)).isEmpty();

        ventanas(media, ventana(SABADO, "01:00", SABADO, "09:00"));
        ventanas(alta, ventana(SABADO, "01:00", SABADO, "09:00"));
        var resumen = planificacion.planificarCiclosAutomaticos();

        assertThat(resumen.ordenesGeneradas()).hasSize(2);
        Orden deAlta = consulta.obtener(ordenesDe(alta).getFirst().getId(), alcanceAdmin);
        Orden deMedia = consulta.obtener(ordenesDe(media).getFirst().getId(), alcanceAdmin);
        Instant inicioAlta = deAlta.programacionVigente().orElseThrow().getFechaInicioProgramada();
        Instant inicioMedia = deMedia.programacionVigente().orElseThrow().getFechaInicioProgramada();

        // La mas critica toma el primer intervalo; con capacidad 1 la otra va despues.
        assertThat(inicioAlta.atZone(LIMA).getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);
        assertThat(inicioAlta.atZone(LIMA).toLocalTime()).isEqualTo(LocalTime.of(1, 0));
        assertThat(inicioMedia).isEqualTo(inicioAlta.plus(Duration.ofHours(4)));
        assertThat(deAlta.getOrigen()).isEqualTo(OrigenOrden.PLANIFICACION_AUTOMATICA);
        assertThat(deAlta.getCodigo()).matches("OM-\\d{4}-\\d{4}");
        assertThat(deAlta.getEstado()).isEqualTo(EstadoOrden.PROGRAMADA);
        assertThat(deAlta.getUsuarioSolicitante()).isNull();
        // RF38: la evaluacion previa empieza el plazo de autorizacion (24 h) antes.
        assertThat(deAlta.programacionVigente().orElseThrow().getFechaEvaluacionProgramada())
                .isEqualTo(inicioAlta.minus(Duration.ofHours(24)));
        assertThat(jdbc.queryForObject("select count(*) from auditoria where operacion = 'GENERAR_ORDEN_AUTOMATICA' "
                + "and proceso = 'PLANIFICACION_AUTOMATICA'", Integer.class)).isEqualTo(2);

        // Una segunda corrida no duplica ciclos.
        assertThat(planificacion.planificarCiclosAutomaticos().ordenesGeneradas()).isEmpty();
    }

    @Test
    @DisplayName("RF27: al configurar un servidor en modalidad automática se genera su ciclo de inmediato")
    void configurarGeneraCiclo() {
        Integer id = servidor("srv-auto", "10.0.0.3", idAlta, responsable);
        ventanas(id, ventana(SABADO, "22:00", DOMINGO, "04:00"));
        configurar(id, ModalidadPlanificacion.AUTOMATICA);

        List<Orden> generadas = ordenesDe(id);
        assertThat(generadas).hasSize(1);
        assertThat(generadas.getFirst().programacionVigente().orElseThrow().getFechaInicioProgramada())
                .isEqualTo(lima("2026-10-03T22:00"));
    }

    @Test
    @DisplayName("RF30: programar con fecha verifica ventana y conflictos; reprogramar conserva el historial; cancelar libera")
    void programarReprogramarCancelar() {
        Integer id = servidor("srv-manual", "10.0.0.4", idMedia, responsable);
        ventanas(id, ventana(SABADO, "01:00", SABADO, "09:00"));
        configurar(id, ModalidadPlanificacion.BAJO_DEMANDA);

        Orden orden = planificacion.programar(id, null, lima("2026-10-03T01:00"), "Parche de seguridad", actorAdmin);
        assertThat(orden.getOrigen()).isEqualTo(OrigenOrden.SOLICITUD_BAJO_DEMANDA);

        // RF51: no se admite otra intervencion superpuesta sobre el mismo servidor.
        assertThatThrownBy(() -> planificacion.programar(id, null, lima("2026-10-03T03:00"), null, actorAdmin))
                .isInstanceOf(PlanificacionImposibleException.class)
                .hasMessageContaining(orden.getCodigo());
        // Fuera de la ventana permisiva.
        assertThatThrownBy(() -> planificacion.programar(id, null, lima("2026-10-03T07:00"), null, actorAdmin))
                .isInstanceOf(PlanificacionImposibleException.class)
                .hasMessageContaining("ventana permisiva");

        planificacion.reprogramar(orden.getId(), lima("2026-10-10T02:00"), "Congelamiento de cambios", actorAdmin);
        Orden reprogramada = consulta.obtener(orden.getId(), alcanceAdmin);
        assertThat(reprogramada.getProgramaciones()).hasSize(2);
        assertThat(reprogramada.programacionVigente().orElseThrow().getMotivo()).isEqualTo("Congelamiento de cambios");
        assertThat(reprogramada.getDetalles().getFirst().getFechaPrevistaInicio()).isEqualTo(lima("2026-10-10T02:00"));
        assertThat(reprogramada.getHistorial()).extracting("estadoNuevo")
                .containsExactly(EstadoOrden.PROGRAMADA, EstadoOrden.REPROGRAMADA, EstadoOrden.PROGRAMADA);
        assertThat(reprogramada.getEstado()).isEqualTo(EstadoOrden.PROGRAMADA);

        planificacion.cancelar(orden.getId(), "Servicio migrado", actorAdmin);
        Orden cancelada = consulta.obtener(orden.getId(), alcanceAdmin);
        assertThat(cancelada.getEstado()).isEqualTo(EstadoOrden.CANCELADA);
        assertThat(cancelada.getDetalles().getFirst().getEstado()).isEqualTo(EstadoDetalleOrden.NO_INICIADO);
        // La reserva queda libre: el mismo horario puede programarse otra vez.
        assertThat(planificacion.programar(id, null, lima("2026-10-10T02:00"), null, actorAdmin)).isNotNull();
    }

    @Test
    @DisplayName("DEC-18: si cambia la ventana, la orden programada que queda fuera se reprograma")
    void cambioDeVentanaReprograma() {
        Integer id = servidor("srv-ventana", "10.0.0.5", idMedia, responsable);
        ventanas(id, ventana(SABADO, "01:00", SABADO, "09:00"));
        configurar(id, ModalidadPlanificacion.BAJO_DEMANDA);
        Orden orden = planificacion.programar(id, null, lima("2026-10-03T01:00"), null, actorAdmin);

        ventanas(id, ventana(DOMINGO, "01:00", DOMINGO, "09:00"));

        Orden actualizada = consulta.obtener(orden.getId(), alcanceAdmin);
        var vigente = actualizada.programacionVigente().orElseThrow();
        assertThat(vigente.getNumeroVersion()).isEqualTo(2);
        assertThat(vigente.getMotivo()).isEqualTo("Cambio de ventana permisiva");
        assertThat(vigente.getFechaInicioProgramada().atZone(LIMA).getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
    }

    @Test
    @DisplayName("RF46/RF72: la baja cancela la orden individual y retira al servidor de la orden grupal")
    void bajaRetiraOrdenes() {
        Integer a = servidor("srv-grupo-a", "10.0.0.6", idMedia, responsable);
        Integer b = servidor("srv-grupo-b", "10.0.0.7", idMedia, responsable);
        for (Integer id : List.of(a, b)) {
            ventanas(id, ventana(SABADO, "00:00", SABADO, "23:00"));
            configurar(id, ModalidadPlanificacion.BAJO_DEMANDA);
        }
        Integer grupo = grupos.crear("Grupo", null, actorAdmin).getId();
        grupos.reemplazarIntegrantes(grupo, List.of(a, b), actorAdmin);
        grupos.configurar(grupo, new ServicioGrupo.DatosConfiguracionGrupo(1, 1, ModalidadPlanificacion.BAJO_DEMANDA,
                ModoEjecucion.SECUENCIAL), actorAdmin);

        Orden individual = planificacion.programar(a, null, lima("2026-10-03T00:00"), null, actorAdmin);
        Orden grupal = planificacion.programar(null, grupo, null, null, actorAdmin);
        Orden grupalLeida = consulta.obtener(grupal.getId(), alcanceAdmin);
        // RF46: el tramo grupal de A no se superpone con su orden individual.
        assertThat(grupalLeida.detalleDe(a).orElseThrow().getFechaPrevistaInicio())
                .isAfterOrEqualTo(lima("2026-10-03T04:00"));
        assertThat(grupalLeida.getDetalles()).extracting("posicionEjecucion").containsExactly(1, 2);

        servidores.solicitarBaja(a, "Retirado", actorAdmin);

        assertThat(consulta.obtener(individual.getId(), alcanceAdmin).getEstado()).isEqualTo(EstadoOrden.CANCELADA);
        Orden grupalTrasBaja = consulta.obtener(grupal.getId(), alcanceAdmin);
        assertThat(grupalTrasBaja.getEstado()).isEqualTo(EstadoOrden.PROGRAMADA);
        assertThat(grupalTrasBaja.detalleDe(a).orElseThrow().getEstado()).isEqualTo(EstadoDetalleOrden.NO_INICIADO);
        assertThat(grupalTrasBaja.detalleDe(b).orElseThrow().getEstado()).isEqualTo(EstadoDetalleOrden.PENDIENTE);
    }

    @Test
    @DisplayName("RF32: el cronograma muestra al responsable solo las órdenes de sus servidores")
    void cronogramaConAlcance() throws Exception {
        Usuario otro = usuarios.crear("resp2.plan", "J. Paredes", "clave-segura-3", Rol.RESPONSABLE, Actor.sistema("PRUEBA"));
        Integer propio = servidor("srv-propio", "10.0.0.8", idMedia, responsable);
        Integer ajeno = servidor("srv-ajeno", "10.0.0.9", idMedia, otro);
        for (Integer id : List.of(propio, ajeno)) {
            ventanas(id, ventana(SABADO, "01:00", SABADO, "09:00"));
            configurar(id, ModalidadPlanificacion.BAJO_DEMANDA);
            planificacion.programar(id, null, lima("2026-10-03T01:00"), null, actorAdmin);
        }
        Instant desde = lima("2026-10-01T00:00");
        Instant hasta = lima("2026-11-01T00:00");

        assertThat(cronograma.entradas(desde, hasta, alcanceAdmin)).hasSize(2);
        assertThat(cronograma.entradas(desde, hasta, new AlcanceUsuario(responsable.getId(), Rol.RESPONSABLE)))
                .extracting(p -> p.getOrden().getServidor().getHostname())
                .containsExactly("srv-propio");

        // RF30: solo el administrador programa; el responsable recibe 403.
        mockMvc.perform(post("/api/ordenes").with(user(new UsuarioAutenticado(responsable)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idServidor\":%d}".formatted(propio)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/ordenes").with(user(new UsuarioAutenticado(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idServidor\":%d,\"inicio\":\"2026-10-10T01:00:00-05:00\",\"motivo\":\"Prueba\"}"
                                .formatted(propio)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resumen.codigo").exists())
                .andExpect(jsonPath("$.resumen.inicioProgramado").value("2026-10-10T06:00:00Z"))
                .andExpect(jsonPath("$.detalles[0].servidor.nombre").value("srv-propio"));
    }
}
