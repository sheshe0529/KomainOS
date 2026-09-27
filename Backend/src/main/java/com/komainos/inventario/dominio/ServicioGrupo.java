package com.komainos.inventario.dominio;

import com.komainos.auditoria.dominio.ServicioAuditoria;
import com.komainos.auditoria.dominio.ServicioAuditoria.Operacion;
import com.komainos.inventario.dominio.ventana.CalendarioSemanal;
import com.komainos.inventario.dominio.ventana.CalendarioSemanal.IntervaloSemanal;
import com.komainos.inventario.infra.ConfiguracionGrupoRepositorio;
import com.komainos.inventario.infra.GrupoMantenimientoRepositorio;
import com.komainos.inventario.infra.ServidorRepositorio;
import com.komainos.seguridad.dominio.AlcanceUsuario;
import com.komainos.shared.dominio.Actor;
import com.komainos.shared.dominio.Tiempo;
import com.komainos.shared.error.ConflictoException;
import com.komainos.shared.error.RecursoNoEncontradoException;
import com.komainos.shared.error.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Grupos de mantenimiento (RF20, RF21, RF46, RF76).
 *
 * <p>La criticidad y la ventana del grupo no se guardan: se derivan de los
 * integrantes cada vez, asi que un cambio en cualquiera de ellos se refleja de
 * inmediato (RF76: «actualizarla cuando cambie la composicion o la criticidad»).
 */
@Service
@RequiredArgsConstructor
public class ServicioGrupo {

    private final GrupoMantenimientoRepositorio grupos;
    private final ConfiguracionGrupoRepositorio configuraciones;
    private final ServidorRepositorio servidores;
    private final ServicioAuditoria auditoria;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    /**
     * Todos los grupos para administrador y operador; para el responsable,
     * los que agrupan sus servidores (por RF20 todos los integrantes de un
     * grupo comparten responsable).
     */
    @Transactional(readOnly = true)
    public List<FichaGrupo> listar(AlcanceUsuario alcance) {
        Map<Integer, ConfiguracionGrupo> configuracionPorGrupo = new HashMap<>();
        configuraciones.findAll().forEach(c -> configuracionPorGrupo.put(c.getGrupo().getId(), c));
        return grupos.findAllByOrderByNombreAsc().stream()
                .filter(g -> esVisible(g, alcance))
                .map(g -> new FichaGrupo(g, Optional.ofNullable(configuracionPorGrupo.get(g.getId())),
                        g.criticidadEfectiva(), null))
                .toList();
    }

    /** HU15 CA4: incluye la criticidad y la ventana calculadas. */
    @Transactional(readOnly = true)
    public FichaGrupo ficha(Integer id, AlcanceUsuario alcance) {
        GrupoMantenimiento grupo = buscar(id);
        if (!esVisible(grupo, alcance)) {
            throw RecursoNoEncontradoException.de("el grupo de mantenimiento", id);
        }
        return new FichaGrupo(grupo, configuraciones.findByGrupoId(id), grupo.criticidadEfectiva(),
                ventanaEfectiva(grupo).aIntervalos());
    }

    @Transactional
    public GrupoMantenimiento crear(String nombre, String descripcion, Actor actor) {
        String limpio = nombre.trim();
        if (grupos.existsByNombreIgnoreCase(limpio)) {
            throw new ConflictoException("Ya existe un grupo con el nombre %s".formatted(limpio));
        }
        GrupoMantenimiento grupo = grupos.save(GrupoMantenimiento.nuevo(limpio, vacioANulo(descripcion)));
        auditoria.registrar(actor, Operacion.de("CREAR_GRUPO", "grupo_mantenimiento", grupo.getId())
                .sobreGrupo(grupo.getId())
                .valores(null, Map.of("nombre", limpio)));
        return grupo;
    }

    @Transactional
    public GrupoMantenimiento actualizar(Integer id, String nombre, String descripcion, Actor actor) {
        GrupoMantenimiento grupo = buscar(id);
        String limpio = nombre.trim();
        if (grupos.existsByNombreIgnoreCaseAndIdNot(limpio, id)) {
            throw new ConflictoException("Ya existe otro grupo con el nombre %s".formatted(limpio));
        }
        Map<String, Object> anterior = new HashMap<>();
        anterior.put("nombre", grupo.getNombre());
        anterior.put("descripcion", grupo.getDescripcion());
        grupo.setNombre(limpio);
        grupo.setDescripcion(vacioANulo(descripcion));
        Map<String, Object> nuevo = new HashMap<>();
        nuevo.put("nombre", limpio);
        nuevo.put("descripcion", grupo.getDescripcion());
        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_GRUPO", "grupo_mantenimiento", id)
                .sobreGrupo(id).valores(anterior, nuevo));
        return grupo;
    }

    /**
     * RF20: define los integrantes. Solo admite servidores no dados de baja con
     * el mismo responsable, entorno y sistema operativo (DEC-20). Las ordenes
     * ya generadas conservan los integrantes que tenian.
     */
    @Transactional
    public GrupoMantenimiento reemplazarIntegrantes(Integer id, List<Integer> idsServidores, Actor actor) {
        GrupoMantenimiento grupo = buscar(id);
        if (grupo.estaInactivo()) {
            throw new ReglaNegocioException("El grupo %s está inactivo; actívelo antes de modificar sus integrantes"
                    .formatted(grupo.getNombre()));
        }
        Set<Integer> ids = new LinkedHashSet<>(idsServidores);
        List<Servidor> nuevos = servidores.findConDetalleByIdIn(ids);
        if (nuevos.size() != ids.size()) {
            Set<Integer> encontrados = new java.util.HashSet<>(nuevos.stream().map(Servidor::getId).toList());
            Integer faltante = ids.stream().filter(i -> !encontrados.contains(i)).findFirst().orElse(null);
            throw RecursoNoEncontradoException.de("el servidor", faltante);
        }
        exigirCompatibles(nuevos);

        List<String> anterior = grupo.servidores().stream().map(Servidor::getHostname).sorted().toList();
        Instant ahora = Tiempo.ahora(reloj);
        grupo.getIntegrantes().removeIf(i -> !ids.contains(i.getServidor().getId()));
        for (Servidor servidor : nuevos) {
            if (!grupo.contiene(servidor.getId())) {
                grupo.agregar(servidor, ahora);
            }
        }
        grupos.flush();

        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_INTEGRANTES", "grupo_mantenimiento", id)
                .sobreGrupo(id)
                .valores(Map.of("integrantes", anterior),
                        Map.of("integrantes", nuevos.stream().map(Servidor::getHostname).sorted().toList())));
        return grupo;
    }

    /**
     * RF17: configuracion del grupo con su modo de ejecucion. Las frecuencias
     * que no se indiquen se toman de la criticidad efectiva del grupo.
     */
    @Transactional
    public ConfiguracionGrupo configurar(Integer id, DatosConfiguracionGrupo datos, Actor actor) {
        GrupoMantenimiento grupo = buscar(id);
        Optional<NivelCriticidad> criticidad = grupo.criticidadEfectiva();
        if ((datos.frecuenciaRevisionDias() == null || datos.frecuenciaMantenimientoDias() == null)
                && criticidad.isEmpty()) {
            throw new ReglaNegocioException(
                    "El grupo no tiene integrantes: indique las frecuencias de revisión y de mantenimiento");
        }
        int revision = datos.frecuenciaRevisionDias() != null
                ? datos.frecuenciaRevisionDias() : criticidad.orElseThrow().getFrecuenciaRevisionDias();
        int mantenimiento = datos.frecuenciaMantenimientoDias() != null
                ? datos.frecuenciaMantenimientoDias() : criticidad.orElseThrow().getFrecuenciaMantenimientoDias();

        Optional<ConfiguracionGrupo> existente = configuraciones.findByGrupoId(id);
        Map<String, Object> anterior = existente.map(ServicioGrupo::instantanea).orElse(null);
        ConfiguracionGrupo configuracion = existente.orElseGet(() -> ConfiguracionGrupo.nueva(
                grupo, revision, mantenimiento, datos.modalidad(), datos.modoEjecucion()));
        configuracion.actualizar(revision, mantenimiento, datos.modalidad(), datos.modoEjecucion());
        configuraciones.save(configuracion);
        grupo.alConfigurar();

        auditoria.registrar(actor, Operacion.de("CONFIGURAR_MANTENIMIENTO", "configuracion_grupo", configuracion.getId())
                .sobreGrupo(id)
                .valores(anterior, instantanea(configuracion)));
        eventos.publishEvent(new ConfiguracionMantenimientoActualizada(null, id));
        return configuracion;
    }

    @Transactional
    public GrupoMantenimiento desactivar(Integer id, Actor actor) {
        GrupoMantenimiento grupo = buscar(id);
        grupo.desactivar();
        auditoria.registrar(actor, Operacion.de("DESACTIVAR_GRUPO", "grupo_mantenimiento", id).sobreGrupo(id));
        return grupo;
    }

    @Transactional
    public GrupoMantenimiento activar(Integer id, Actor actor) {
        GrupoMantenimiento grupo = buscar(id);
        grupo.activar(configuraciones.findByGrupoId(id).isPresent());
        auditoria.registrar(actor, Operacion.de("ACTIVAR_GRUPO", "grupo_mantenimiento", id).sobreGrupo(id));
        return grupo;
    }

    /** RF21: interseccion de las ventanas de los integrantes. */
    public static CalendarioSemanal ventanaEfectiva(GrupoMantenimiento grupo) {
        return CalendarioSemanal.interseccionDe(grupo.servidores().stream()
                .map(s -> CalendarioSemanal.de(s.getVentanas()))
                .toList());
    }

    // ------------------------------------------------------------- internos

    private GrupoMantenimiento buscar(Integer id) {
        return grupos.findConIntegrantesById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el grupo de mantenimiento", id));
    }

    private static boolean esVisible(GrupoMantenimiento grupo, AlcanceUsuario alcance) {
        return alcance.veTodoElInventario()
                || grupo.servidores().stream().anyMatch(s -> s.esVisiblePara(alcance.usuarioId()));
    }

    private static void exigirCompatibles(List<Servidor> candidatos) {
        if (candidatos.isEmpty()) {
            return;
        }
        Servidor referencia = candidatos.getFirst();
        for (Servidor s : candidatos) {
            if (s.estaDadoDeBaja()) {
                throw new ReglaNegocioException("El servidor %s está dado de baja y no puede integrar grupos"
                        .formatted(s.getHostname()));
            }
            if (!Objects.equals(s.getResponsable().getId(), referencia.getResponsable().getId())) {
                throw new ReglaNegocioException(
                        "Los integrantes deben tener el mismo responsable: %s y %s difieren"
                                .formatted(referencia.getHostname(), s.getHostname()));
            }
            if (!Objects.equals(s.getEntorno().getId(), referencia.getEntorno().getId())) {
                throw new ReglaNegocioException(
                        "Los integrantes deben pertenecer al mismo entorno: %s y %s difieren"
                                .formatted(referencia.getHostname(), s.getHostname()));
            }
            Integer so = s.getVersionSistemaOperativo().getSistemaOperativo().getId();
            Integer soReferencia = referencia.getVersionSistemaOperativo().getSistemaOperativo().getId();
            if (!Objects.equals(so, soReferencia)) {
                throw new ReglaNegocioException(
                        "Los integrantes deben tener el mismo sistema operativo: %s y %s difieren"
                                .formatted(referencia.getHostname(), s.getHostname()));
            }
        }
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static Map<String, Object> instantanea(ConfiguracionGrupo c) {
        Map<String, Object> v = new HashMap<>();
        v.put("frecuenciaRevisionDias", c.getFrecuenciaRevisionDias());
        v.put("frecuenciaMantenimientoDias", c.getFrecuenciaMantenimientoDias());
        v.put("modalidadPlanificacion", c.getModalidadPlanificacion().name());
        v.put("modoEjecucion", c.getModoEjecucion().name());
        v.put("idCuentaServicio", c.getIdCuentaServicio());
        return v;
    }

    public record DatosConfiguracionGrupo(Integer frecuenciaRevisionDias, Integer frecuenciaMantenimientoDias,
                                          ModalidadPlanificacion modalidad, ModoEjecucion modoEjecucion) {
    }

    /**
     * Grupo con sus valores derivados. {@code ventanaEfectiva} es nula en el
     * listado para no calcularla por cada grupo.
     */
    public record FichaGrupo(GrupoMantenimiento grupo, Optional<ConfiguracionGrupo> configuracion,
                             Optional<NivelCriticidad> criticidadEfectiva, List<IntervaloSemanal> ventanaEfectiva) {
    }
}
