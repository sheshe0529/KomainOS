package com.komainos.inventario.service;

import com.komainos.auditoria.model.RegistroAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.event.ConfiguracionMantenimientoActualizada;
import com.komainos.inventario.event.VentanasServidorActualizadas;
import com.komainos.inventario.model.CalendarioSemanal.IntervaloSemanal;
import com.komainos.inventario.model.ConfiguracionServidor;
import com.komainos.inventario.model.DatosServidor;
import com.komainos.inventario.model.Entorno;
import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.EstadoSolicitudBaja;
import com.komainos.inventario.model.FiltroServidores;
import com.komainos.inventario.model.GrupoMantenimiento;
import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.model.NivelCriticidad;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.model.SolicitudBaja;
import com.komainos.inventario.model.VentanaMantenimiento;
import com.komainos.inventario.model.VersionSistemaOperativo;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.repository.EspecificacionesServidor;
import com.komainos.inventario.repository.GrupoMantenimientoRepositorio;
import com.komainos.inventario.repository.ServidorRepositorio;
import com.komainos.inventario.repository.SolicitudBajaRepositorio;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.repository.UsuarioRepositorio;
import com.komainos.seguridad.service.PuertoServidoresACargo;
import com.komainos.shared.exception.ConflictoException;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Casos de uso del inventario de servidores (RF09-RF11, RF14, RF17-RF19,
 * RF70, RF72, RF73).
 *
 * <p>Devuelve entidades con las asociaciones que la API necesita ya cargadas
 * ({@code open-in-view} esta desactivado); el mapeo a DTO ocurre en el
 * controlador, dentro de lo que esta clase dejo inicializado.
 */
@Service
@RequiredArgsConstructor
public class ServicioServidor implements PuertoServidoresACargo {

    private final ServidorRepositorio servidores;
    private final ConfiguracionServidorRepositorio configuraciones;
    private final SolicitudBajaRepositorio solicitudesBaja;
    private final GrupoMantenimientoRepositorio grupos;
    private final UsuarioRepositorio usuarios;
    private final ServicioCatalogos catalogos;
    private final PuertoMantenimientos mantenimientos;
    private final ServicioAuditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    // ----------------------------------------------------------------- consulta

    /** RF11: busqueda, orden y filtros dentro del alcance del usuario. */
    @Transactional(readOnly = true)
    public Page<Servidor> listar(FiltroServidores filtro, AlcanceUsuario alcance, Pageable paginacion) {
        return servidores.findAll(EspecificacionesServidor.con(filtro, alcance), paginacion);
    }

    /**
     * Fuera de alcance responde "no encontrado" y no "acceso denegado": lo
     * contrario le confirmaria al responsable que existe un servidor ajeno.
     */
    @Transactional(readOnly = true)
    public Servidor obtener(Integer id, AlcanceUsuario alcance) {
        Servidor servidor = buscar(id);
        if (!alcance.veTodoElInventario() && !servidor.esVisiblePara(alcance.usuarioId())) {
            throw RecursoNoEncontradoException.de("el servidor", id);
        }
        return servidor;
    }

    /** Servidores vigentes (no dados de baja) de los que un usuario es responsable. */
    @Transactional(readOnly = true)
    @Override
    public long contarACargoDe(Integer idUsuario) {
        return servidores.countByResponsableIdAndEstadoNot(idUsuario, EstadoServidor.DADO_DE_BAJA);
    }

    /** RF14 / HU10: informacion consolidada del servidor. */
    @Transactional(readOnly = true)
    public FichaServidor ficha(Integer id, AlcanceUsuario alcance) {
        Servidor servidor = obtener(id, alcance);
        List<SolicitudBaja> bajas = solicitudesBaja.findByServidorIdOrderByFechaSolicitudDesc(id);
        return new FichaServidor(
                servidor,
                configuraciones.findByServidorId(id),
                grupos.findDelServidor(id),
                bajas.stream().filter(b -> b.getEstado() == EstadoSolicitudBaja.PENDIENTE).findFirst(),
                bajas,
                reactivaciones(id));
    }

    /**
     * RF73: las reactivaciones no tienen tabla propia; quedan en la bitacora
     * con quien las hizo (RNF06).
     */
    private List<Reactivacion> reactivaciones(Integer idServidor) {
        List<RegistroAuditoria> registros = auditoria.consultarSobreServidor(idServidor, "REACTIVAR_SERVIDOR");
        Map<Integer, Usuario> autores = new HashMap<>();
        usuarios.findAllById(registros.stream().map(RegistroAuditoria::getIdUsuario).filter(Objects::nonNull).toList())
                .forEach(u -> autores.put(u.getId(), u));
        return registros.stream()
                .map(r -> new Reactivacion(r.getFechaHora(), r.getIdUsuario() == null ? null : autores.get(r.getIdUsuario())))
                .toList();
    }

    // ------------------------------------------------------------ alta y edicion

    /**
     * RF09 / HU06: alta con deteccion de duplicados por hostname e IP. Nace
     * pendiente de configuracion (HU06 CA3).
     */
    @Transactional
    public Servidor crear(DatosServidor datos, Actor actor) {
        DatosServidor limpio = normalizar(datos);
        if (servidores.existsByHostnameIgnoreCase(limpio.hostname())) {
            throw new ConflictoException("Ya existe un servidor con el hostname %s".formatted(limpio.hostname()));
        }
        if (servidores.existsByDireccionIp(limpio.direccionIp())) {
            throw new ConflictoException("Ya existe un servidor con la dirección IP %s".formatted(limpio.direccionIp()));
        }
        Servidor servidor = Servidor.nuevo();
        aplicar(limpio, servidor, true);
        servidores.save(servidor);
        auditoria.registrar(actor, Operacion.de("CREAR_SERVIDOR", "servidor", servidor.getId())
                .sobreServidor(servidor.getId())
                .valores(null, instantanea(servidor)));
        return servidor;
    }

    @Transactional
    public Servidor actualizar(Integer id, DatosServidor datos, Actor actor) {
        Servidor servidor = buscar(id);
        servidor.exigirNoDadoDeBaja("editarlo");
        DatosServidor limpio = normalizar(datos);
        if (servidores.existsByHostnameIgnoreCaseAndIdNot(limpio.hostname(), id)) {
            throw new ConflictoException("Ya existe otro servidor con el hostname %s".formatted(limpio.hostname()));
        }
        if (servidores.existsByDireccionIpAndIdNot(limpio.direccionIp(), id)) {
            throw new ConflictoException("Ya existe otro servidor con la dirección IP %s".formatted(limpio.direccionIp()));
        }
        exigirCoherenciaConGrupos(servidor, limpio);

        Map<String, Object> anterior = instantanea(servidor);
        aplicar(limpio, servidor, false);
        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_SERVIDOR", "servidor", id)
                .sobreServidor(id)
                .valores(anterior, instantanea(servidor)));
        return servidor;
    }

    // ----------------------------------------------------------- configuracion

    /**
     * RF17 / RF70 / HU13: crea o modifica la configuracion de mantenimiento.
     * Las frecuencias que no se indiquen se copian del nivel de criticidad
     * (HU11 CA2). Guardarla habilita el servidor (DEC-14). Los cambios afectan
     * solo a las ordenes nuevas (HU13 CA7).
     */
    @Transactional
    public ConfiguracionServidor configurar(Integer id, DatosConfiguracion datos, Actor actor) {
        Servidor servidor = buscar(id);
        servidor.exigirNoDadoDeBaja("configurarlo");
        NivelCriticidad criticidad = servidor.getNivelCriticidad();
        int revision = datos.frecuenciaRevisionDias() != null
                ? datos.frecuenciaRevisionDias() : criticidad.getFrecuenciaRevisionDias();
        int mantenimiento = datos.frecuenciaMantenimientoDias() != null
                ? datos.frecuenciaMantenimientoDias() : criticidad.getFrecuenciaMantenimientoDias();

        Optional<ConfiguracionServidor> existente = configuraciones.findByServidorId(id);
        Map<String, Object> anterior = existente.map(ServicioServidor::instantanea).orElse(null);
        ConfiguracionServidor configuracion = existente.orElseGet(() ->
                ConfiguracionServidor.nueva(servidor, revision, mantenimiento, datos.modalidad()));
        configuracion.actualizar(revision, mantenimiento, datos.modalidad());
        configuraciones.save(configuracion);
        servidor.activarPorConfiguracion();

        auditoria.registrar(actor, Operacion.de("CONFIGURAR_MANTENIMIENTO", "configuracion_servidor", configuracion.getId())
                .sobreServidor(id)
                .valores(anterior, instantanea(configuracion)));
        eventos.publishEvent(new ConfiguracionMantenimientoActualizada(id, null));
        return configuracion;
    }

    // ----------------------------------------------------------------- ventanas

    /**
     * RF18 / RF19 / HU14: reemplaza la ventana permisiva. El administrador
     * puede editar cualquier servidor; el responsable, solo los suyos. Los
     * cambios se aplican a los proximos mantenimientos (DEC-18).
     */
    @Transactional
    public Servidor reemplazarVentanas(Integer id, List<IntervaloSemanal> intervalos, AlcanceUsuario alcance) {
        Servidor servidor = obtener(id, alcance);
        servidor.exigirNoDadoDeBaja("editar su ventana permisiva");

        List<Map<String, Object>> anterior = servidor.getVentanas().stream().map(ServicioServidor::instantanea).toList();
        List<VentanaMantenimiento> nuevas = intervalos.stream()
                .map(i -> VentanaMantenimiento.nueva(i.diaInicio(), i.horaInicio(), i.diaFin(), i.horaFin()))
                .toList();
        servidor.reemplazarVentanas(nuevas);
        servidores.flush();

        auditoria.registrar(alcance.actor(), Operacion.de("ACTUALIZAR_VENTANAS", "servidor", id)
                .sobreServidor(id)
                .valores(anterior, nuevas.stream().map(ServicioServidor::instantanea).toList()));
        eventos.publishEvent(VentanasServidorActualizadas.deServidor(id, alcance.actor()));
        return servidor;
    }

    // -------------------------------------------------------- baja y reactivacion

    /**
     * RF72 / HU06 CA5-CA6: registra la solicitud de baja. Si hay un
     * mantenimiento en curso la solicitud queda pendiente hasta que termine;
     * si no, se aplica de inmediato: retira los mantenimientos pendientes y
     * las pertenencias a grupos, elimina la configuracion vigente (HU13 CA9)
     * y conserva todo el historial.
     */
    @Transactional
    public ResultadoBaja solicitarBaja(Integer id, String motivo, Actor actor) {
        Servidor servidor = buscar(id);
        if (servidor.estaDadoDeBaja()) {
            throw new ReglaNegocioException("El servidor %s ya se encuentra dado de baja".formatted(servidor.getHostname()));
        }
        if (solicitudesBaja.findFirstByServidorIdAndEstado(id, EstadoSolicitudBaja.PENDIENTE).isPresent()) {
            throw new ConflictoException("El servidor %s ya tiene una solicitud de baja pendiente"
                    .formatted(servidor.getHostname()));
        }
        Instant ahora = Tiempo.ahora(reloj);
        Usuario solicitante = usuarioDe(actor);
        SolicitudBaja solicitud = solicitudesBaja.save(SolicitudBaja.nueva(servidor, solicitante, motivo.trim(), ahora));
        auditoria.registrar(actor, Operacion.de("SOLICITAR_BAJA", "solicitud_baja", solicitud.getId())
                .sobreServidor(id)
                .motivo(motivo));

        if (mantenimientos.tieneMantenimientoEnCurso(id)) {
            return new ResultadoBaja(servidor, solicitud, false, 0);
        }
        int retiradas = aplicarBaja(servidor, solicitud, solicitante, actor, ahora);
        return new ResultadoBaja(servidor, solicitud, true, retiradas);
    }

    /** RF73 / HU06 CA7: vuelve a pendiente de configuracion. */
    @Transactional
    public Servidor reactivar(Integer id, Actor actor) {
        Servidor servidor = buscar(id);
        servidor.reactivar();
        auditoria.registrar(actor, Operacion.de("REACTIVAR_SERVIDOR", "servidor", id).sobreServidor(id));
        return servidor;
    }

    private int aplicarBaja(Servidor servidor, SolicitudBaja solicitud, Usuario autor, Actor actor, Instant ahora) {
        Integer id = servidor.getId();
        int retiradas = mantenimientos.retirarPendientes(id, autor,
                "Baja del servidor %s: %s".formatted(servidor.getHostname(), solicitud.getMotivo()), ahora);

        for (GrupoMantenimiento grupo : grupos.findDelServidor(id)) {
            grupo.quitar(id);
            auditoria.registrar(actor, Operacion.de("RETIRAR_INTEGRANTE", "grupo_mantenimiento", grupo.getId())
                    .sobreGrupo(grupo.getId())
                    .sobreServidor(id)
                    .motivo("Baja del servidor"));
        }

        configuraciones.findByServidorId(id).ifPresent(configuracion -> {
            auditoria.registrar(actor, Operacion.de("ELIMINAR_CONFIGURACION", "configuracion_servidor", configuracion.getId())
                    .sobreServidor(id)
                    .valores(instantanea(configuracion), null)
                    .motivo("Baja del servidor"));
            configuraciones.delete(configuracion);
        });

        servidor.aplicarBaja();
        solicitud.aplicar(ahora);
        auditoria.registrar(actor, Operacion.de("APLICAR_BAJA", "servidor", id)
                .sobreServidor(id)
                .valores(null, Map.of("ordenesRetiradas", retiradas))
                .motivo(solicitud.getMotivo()));
        return retiradas;
    }

    // ------------------------------------------------------------- utilidades

    private Servidor buscar(Integer id) {
        return servidores.findConDetalleById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el servidor", id));
    }

    /**
     * Resuelve las referencias y vuelca los datos. Al editar, una referencia
     * que no cambio puede seguir apuntando a un elemento ya desactivado; solo
     * se exige que este activo lo que se asigna de nuevo.
     */
    private void aplicar(DatosServidor d, Servidor s, boolean esAlta) {
        VersionSistemaOperativo version = catalogos.obtenerVersion(d.idVersionSistemaOperativo());
        if (cambia(esAlta, s.getVersionSistemaOperativo(), version.getId())
                && (!version.isActivo() || !version.getSistemaOperativo().isActivo())) {
            throw new ReglaNegocioException("La versión de sistema operativo %s no está activa"
                    .formatted(version.descripcionCompleta()));
        }
        Entorno entorno = catalogos.obtenerEntorno(d.idEntorno());
        if (cambia(esAlta, s.getEntorno(), entorno.getId()) && !entorno.isActivo()) {
            throw new ReglaNegocioException("El entorno %s no está activo".formatted(entorno.getNombre()));
        }
        NivelCriticidad criticidad = catalogos.obtenerCriticidad(d.idNivelCriticidad());
        if (cambia(esAlta, s.getNivelCriticidad(), criticidad.getId()) && !criticidad.isActivo()) {
            throw new ReglaNegocioException("El nivel de criticidad %s no está activo".formatted(criticidad.getNombre()));
        }
        Usuario responsable = usuarios.findById(d.idResponsable())
                .orElseThrow(() -> RecursoNoEncontradoException.de("el responsable", d.idResponsable()));
        if (cambia(esAlta, s.getResponsable(), responsable.getId())) {
            exigirResponsableValido(responsable);
        }

        s.setHostname(d.hostname());
        s.setDireccionIp(d.direccionIp());
        s.setDatacenter(d.datacenter());
        s.setServidorFisico(d.servidorFisico());
        s.setVlan(d.vlan());
        s.setCluster(d.cluster());
        s.setDns(d.dns());
        s.setPlataforma(d.plataforma());
        s.setDescripcion(d.descripcion());
        s.setVersionSistemaOperativo(version);
        s.setEntorno(entorno);
        s.setNivelCriticidad(criticidad);
        s.setResponsable(responsable);
    }

    /** El responsable autoriza y valida los mantenimientos de sus servidores (DEC-24). */
    private static void exigirResponsableValido(Usuario responsable) {
        motivoResponsableInvalido(responsable).ifPresent(motivo -> {
            throw new ReglaNegocioException(motivo);
        });
    }

    /** Motivo por el que un usuario no puede ser responsable de servidores (DEC-24), o vacío. */
    public static Optional<String> motivoResponsableInvalido(Usuario responsable) {
        if (!responsable.isActivo()) {
            return Optional.of("El usuario %s no está activo".formatted(responsable.getCodigo()));
        }
        if (responsable.getRol() != Rol.RESPONSABLE) {
            return Optional.of("El usuario %s no tiene el rol Responsable".formatted(responsable.getCodigo()));
        }
        return Optional.empty();
    }

    private void exigirCoherenciaConGrupos(Servidor servidor, DatosServidor nuevos) {
        conflictoConGrupos(servidor, nuevos).ifPresent(motivo -> {
            throw new ReglaNegocioException(motivo);
        });
    }

    /**
     * RF20: los integrantes de un grupo comparten responsable, entorno y
     * sistema operativo. Si el servidor pertenece a grupos, esos atributos no
     * pueden cambiar sin retirarlo antes. Devuelve el motivo del conflicto, o
     * vacío; la importación (RF12) lo usa para anticiparlo en la vista previa.
     */
    @Transactional(readOnly = true)
    public Optional<String> conflictoConGrupos(Servidor servidor, DatosServidor nuevos) {
        List<GrupoMantenimiento> suyos = grupos.findDelServidor(servidor.getId());
        if (suyos.isEmpty()) {
            return Optional.empty();
        }
        Integer soActual = servidor.getVersionSistemaOperativo().getSistemaOperativo().getId();
        Integer soNuevo = catalogos.obtenerVersion(nuevos.idVersionSistemaOperativo()).getSistemaOperativo().getId();
        boolean cambiaAtributoDeGrupo = !Objects.equals(servidor.getResponsable().getId(), nuevos.idResponsable())
                || !Objects.equals(servidor.getEntorno().getId(), nuevos.idEntorno())
                || !Objects.equals(soActual, soNuevo);
        if (!cambiaAtributoDeGrupo) {
            return Optional.empty();
        }
        return Optional.of(("El servidor %s pertenece al grupo %s: su responsable, entorno y sistema "
                + "operativo deben coincidir con los del grupo. Retírelo del grupo antes de cambiarlos")
                .formatted(servidor.getHostname(), suyos.getFirst().getNombre()));
    }

    private static boolean cambia(boolean esAlta, Object actual, Integer idNuevo) {
        if (esAlta || actual == null) {
            return true;
        }
        Integer idActual = switch (actual) {
            case VersionSistemaOperativo v -> v.getId();
            case Entorno e -> e.getId();
            case NivelCriticidad n -> n.getId();
            case Usuario u -> u.getId();
            default -> null;
        };
        return !Objects.equals(idActual, idNuevo);
    }

    private Usuario usuarioDe(Actor actor) {
        if (actor.esSistema()) {
            throw new ReglaNegocioException("La baja de un servidor la solicita un usuario administrador");
        }
        return usuarios.findById(actor.usuarioId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("el usuario", actor.usuarioId()));
    }

    private static DatosServidor normalizar(DatosServidor d) {
        return new DatosServidor(recortar(d.hostname()), recortar(d.direccionIp()).toLowerCase(),
                vacioANulo(d.datacenter()), vacioANulo(d.servidorFisico()), vacioANulo(d.vlan()),
                vacioANulo(d.cluster()), vacioANulo(d.dns()), d.idVersionSistemaOperativo(),
                vacioANulo(d.plataforma()), d.idEntorno(), d.idNivelCriticidad(), d.idResponsable(),
                vacioANulo(d.descripcion()));
    }

    private static String recortar(String valor) {
        return valor == null ? null : valor.trim();
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static Map<String, Object> instantanea(Servidor s) {
        Map<String, Object> v = new HashMap<>();
        v.put("hostname", s.getHostname());
        v.put("direccionIp", s.getDireccionIp());
        v.put("datacenter", s.getDatacenter());
        v.put("servidorFisico", s.getServidorFisico());
        v.put("vlan", s.getVlan());
        v.put("cluster", s.getCluster());
        v.put("dns", s.getDns());
        v.put("plataforma", s.getPlataforma());
        v.put("descripcion", s.getDescripcion());
        v.put("idVersionSistemaOperativo", s.getVersionSistemaOperativo().getId());
        v.put("idEntorno", s.getEntorno().getId());
        v.put("idNivelCriticidad", s.getNivelCriticidad().getId());
        v.put("idResponsable", s.getResponsable().getId());
        v.put("estado", s.getEstado().name());
        return v;
    }

    private static Map<String, Object> instantanea(ConfiguracionServidor c) {
        Map<String, Object> v = new HashMap<>();
        v.put("frecuenciaRevisionDias", c.getFrecuenciaRevisionDias());
        v.put("frecuenciaMantenimientoDias", c.getFrecuenciaMantenimientoDias());
        v.put("modalidadPlanificacion", c.getModalidadPlanificacion().name());
        v.put("idCuentaServicio", c.getIdCuentaServicio());
        return v;
    }

    private static Map<String, Object> instantanea(VentanaMantenimiento w) {
        return Map.of("diaInicio", w.getDiaInicio().name(), "horaInicio", w.getHoraInicio().toString(),
                "diaFin", w.getDiaFin().name(), "horaFin", w.getHoraFin().toString());
    }

    /** Datos de la configuracion de mantenimiento de un servidor (RF17). */
    public record DatosConfiguracion(Integer frecuenciaRevisionDias, Integer frecuenciaMantenimientoDias,
                                     ModalidadPlanificacion modalidad) {
    }

    /** Informacion consolidada de la ficha (RF14). */
    /**
     * @param bajas          solicitudes de baja, la más reciente primero (RF72)
     * @param reactivaciones reactivaciones, la más reciente primero (RF73)
     */
    public record FichaServidor(Servidor servidor, Optional<ConfiguracionServidor> configuracion,
                                List<GrupoMantenimiento> grupos, Optional<SolicitudBaja> bajaPendiente,
                                List<SolicitudBaja> bajas, List<Reactivacion> reactivaciones) {
    }

    /** @param usuario quien reactivó; nulo si lo hizo un proceso del sistema */
    public record Reactivacion(Instant fecha, Usuario usuario) {
    }

    /**
     * Resultado de solicitar la baja: si se aplico de inmediato o quedo
     * pendiente por un mantenimiento en curso, y cuantas ordenes se retiraron.
     */
    public record ResultadoBaja(Servidor servidor, SolicitudBaja solicitud, boolean aplicada, int ordenesRetiradas) {
    }
}
