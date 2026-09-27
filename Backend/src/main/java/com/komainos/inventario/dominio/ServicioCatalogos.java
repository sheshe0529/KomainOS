package com.komainos.inventario.dominio;

import com.komainos.auditoria.dominio.ServicioAuditoria;
import com.komainos.auditoria.dominio.ServicioAuditoria.Operacion;
import com.komainos.inventario.dominio.NivelCriticidad.DatosNivelCriticidad;
import com.komainos.inventario.infra.EntornoRepositorio;
import com.komainos.inventario.infra.NivelCriticidadRepositorio;
import com.komainos.inventario.infra.SistemaOperativoRepositorio;
import com.komainos.inventario.infra.VersionSistemaOperativoRepositorio;
import com.komainos.shared.dominio.Actor;
import com.komainos.shared.error.ConflictoException;
import com.komainos.shared.error.RecursoNoEncontradoException;
import com.komainos.shared.error.ReglaNegocioException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogos de referencia del inventario: entornos (RF74), niveles de
 * criticidad (RF15) y sistemas operativos con sus versiones (dependencia de
 * RF10). Son catalogos acotados, por eso sus listados no se paginan.
 */
@Service
@RequiredArgsConstructor
public class ServicioCatalogos {

    private final EntornoRepositorio entornos;
    private final NivelCriticidadRepositorio criticidades;
    private final SistemaOperativoRepositorio sistemas;
    private final VersionSistemaOperativoRepositorio versiones;
    private final ServicioAuditoria auditoria;

    // ---------------------------------------------------------------- entornos

    @Transactional(readOnly = true)
    public List<Entorno> listarEntornos() {
        return entornos.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public Entorno obtenerEntorno(Integer id) {
        return entornos.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("el entorno", id));
    }

    @Transactional
    public Entorno crearEntorno(String nombre, String descripcion, Actor actor) {
        String limpio = nombre.trim();
        if (entornos.existsByNombreIgnoreCase(limpio)) {
            throw new ConflictoException("Ya existe un entorno con el nombre %s".formatted(limpio));
        }
        Entorno entorno = entornos.save(Entorno.nuevo(limpio, descripcion));
        auditoria.registrar(actor, Operacion.de("CREAR_ENTORNO", "entorno", entorno.getId())
                .valores(null, instantanea(entorno)));
        return entorno;
    }

    @Transactional
    public Entorno actualizarEntorno(Integer id, String nombre, String descripcion, Actor actor) {
        Entorno entorno = obtenerEntorno(id);
        String limpio = nombre.trim();
        if (entornos.existsByNombreIgnoreCaseAndIdNot(limpio, id)) {
            throw new ConflictoException("Ya existe otro entorno con el nombre %s".formatted(limpio));
        }
        Map<String, Object> anterior = instantanea(entorno);
        entorno.setNombre(limpio);
        entorno.setDescripcion(descripcion);
        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_ENTORNO", "entorno", id)
                .valores(anterior, instantanea(entorno)));
        return entorno;
    }

    /** RF74: se desactiva para conservar sus referencias historicas. */
    @Transactional
    public Entorno cambiarEstadoEntorno(Integer id, boolean activar, Actor actor) {
        Entorno entorno = obtenerEntorno(id);
        if (activar) {
            entorno.activar();
        } else {
            entorno.desactivar();
        }
        auditoria.registrar(actor, Operacion.de(activar ? "ACTIVAR_ENTORNO" : "DESACTIVAR_ENTORNO", "entorno", id));
        return entorno;
    }

    // ------------------------------------------------------ niveles de criticidad

    @Transactional(readOnly = true)
    public List<NivelCriticidad> listarCriticidades() {
        return criticidades.findAllByOrderByPrioridadAsc();
    }

    @Transactional(readOnly = true)
    public NivelCriticidad obtenerCriticidad(Integer id) {
        return criticidades.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el nivel de criticidad", id));
    }

    @Transactional
    public NivelCriticidad crearCriticidad(DatosNivelCriticidad datos, Actor actor) {
        DatosNivelCriticidad limpio = normalizar(datos);
        if (criticidades.existsByNombreIgnoreCase(limpio.nombre())) {
            throw new ConflictoException("Ya existe un nivel de criticidad con el nombre %s".formatted(limpio.nombre()));
        }
        if (criticidades.existsByPrioridad(limpio.prioridad())) {
            throw new ConflictoException("Ya existe un nivel de criticidad con la prioridad %d".formatted(limpio.prioridad()));
        }
        NivelCriticidad nivel = criticidades.save(NivelCriticidad.nuevo(limpio));
        auditoria.registrar(actor, Operacion.de("CREAR_NIVEL_CRITICIDAD", "nivel_criticidad", nivel.getId())
                .valores(null, instantanea(nivel)));
        return nivel;
    }

    /**
     * HU11 CA2: el cambio no se propaga a las configuraciones de mantenimiento
     * ya creadas, que conservan los valores copiados al crearse.
     */
    @Transactional
    public NivelCriticidad actualizarCriticidad(Integer id, DatosNivelCriticidad datos, Actor actor) {
        NivelCriticidad nivel = obtenerCriticidad(id);
        DatosNivelCriticidad limpio = normalizar(datos);
        if (criticidades.existsByNombreIgnoreCaseAndIdNot(limpio.nombre(), id)) {
            throw new ConflictoException("Ya existe otro nivel de criticidad con el nombre %s".formatted(limpio.nombre()));
        }
        if (criticidades.existsByPrioridadAndIdNot(limpio.prioridad(), id)) {
            throw new ConflictoException("Ya existe otro nivel de criticidad con la prioridad %d".formatted(limpio.prioridad()));
        }
        Map<String, Object> anterior = instantanea(nivel);
        nivel.aplicar(limpio);
        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_NIVEL_CRITICIDAD", "nivel_criticidad", id)
                .valores(anterior, instantanea(nivel)));
        return nivel;
    }

    @Transactional
    public NivelCriticidad cambiarEstadoCriticidad(Integer id, boolean activar, Actor actor) {
        NivelCriticidad nivel = obtenerCriticidad(id);
        if (activar) {
            nivel.activar();
        } else {
            nivel.desactivar();
        }
        auditoria.registrar(actor, Operacion.de(
                activar ? "ACTIVAR_NIVEL_CRITICIDAD" : "DESACTIVAR_NIVEL_CRITICIDAD", "nivel_criticidad", id));
        return nivel;
    }

    /** HU11 CA3: impide eliminar un nivel asignado a uno o mas servidores. */
    @Transactional
    public void eliminarCriticidad(Integer id, Actor actor) {
        NivelCriticidad nivel = obtenerCriticidad(id);
        if (criticidades.estaEnUso(id)) {
            throw new ReglaNegocioException(
                    "No se puede eliminar el nivel %s porque está asignado a servidores u órdenes; puede desactivarlo"
                            .formatted(nivel.getNombre()));
        }
        Map<String, Object> anterior = instantanea(nivel);
        criticidades.delete(nivel);
        auditoria.registrar(actor, Operacion.de("ELIMINAR_NIVEL_CRITICIDAD", "nivel_criticidad", id)
                .valores(anterior, null));
    }

    // ---------------------------------------------------- sistemas operativos

    @Transactional(readOnly = true)
    public List<SistemaOperativo> listarSistemasOperativos() {
        return sistemas.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public SistemaOperativo obtenerSistemaOperativo(Integer id) {
        return sistemas.findConVersionesById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el sistema operativo", id));
    }

    @Transactional
    public SistemaOperativo crearSistemaOperativo(String nombre, FamiliaSistemaOperativo familia, Actor actor) {
        String limpio = nombre.trim();
        if (sistemas.existsByNombreIgnoreCase(limpio)) {
            throw new ConflictoException("Ya existe un sistema operativo con el nombre %s".formatted(limpio));
        }
        SistemaOperativo so = sistemas.save(SistemaOperativo.nuevo(limpio, familia));
        auditoria.registrar(actor, Operacion.de("CREAR_SISTEMA_OPERATIVO", "sistema_operativo", so.getId())
                .valores(null, Map.of("nombre", limpio, "familia", familia.name())));
        return so;
    }

    @Transactional
    public SistemaOperativo actualizarSistemaOperativo(Integer id, String nombre, FamiliaSistemaOperativo familia,
                                                       boolean activo, Actor actor) {
        SistemaOperativo so = obtenerSistemaOperativo(id);
        String limpio = nombre.trim();
        if (sistemas.existsByNombreIgnoreCaseAndIdNot(limpio, id)) {
            throw new ConflictoException("Ya existe otro sistema operativo con el nombre %s".formatted(limpio));
        }
        Map<String, Object> anterior = Map.of("nombre", so.getNombre(), "familia", so.getFamilia().name(),
                "activo", so.isActivo());
        so.setNombre(limpio);
        so.setFamilia(familia);
        so.setActivo(activo);
        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_SISTEMA_OPERATIVO", "sistema_operativo", id)
                .valores(anterior, Map.of("nombre", limpio, "familia", familia.name(), "activo", activo)));
        return so;
    }

    @Transactional
    public SistemaOperativo agregarVersion(Integer idSistemaOperativo, String version, Actor actor) {
        SistemaOperativo so = obtenerSistemaOperativo(idSistemaOperativo);
        String limpia = version.trim();
        if (versiones.existsBySistemaOperativoIdAndVersionIgnoreCase(idSistemaOperativo, limpia)) {
            throw new ConflictoException("El sistema operativo %s ya tiene la versión %s"
                    .formatted(so.getNombre(), limpia));
        }
        VersionSistemaOperativo nueva = versiones.save(VersionSistemaOperativo.nueva(so, limpia));
        so.getVersiones().add(nueva);
        auditoria.registrar(actor, Operacion.de("CREAR_VERSION_SO", "version_sistema_operativo", nueva.getId())
                .valores(null, Map.of("sistemaOperativo", so.getNombre(), "version", limpia)));
        return so;
    }

    @Transactional
    public SistemaOperativo actualizarVersion(Integer idVersion, String version, boolean activo, Actor actor) {
        VersionSistemaOperativo v = obtenerVersion(idVersion);
        String limpia = version.trim();
        Integer idSo = v.getSistemaOperativo().getId();
        if (versiones.existsBySistemaOperativoIdAndVersionIgnoreCaseAndIdNot(idSo, limpia, idVersion)) {
            throw new ConflictoException("El sistema operativo ya tiene la versión %s".formatted(limpia));
        }
        Map<String, Object> anterior = Map.of("version", v.getVersion(), "activo", v.isActivo());
        v.setVersion(limpia);
        v.setActivo(activo);
        auditoria.registrar(actor, Operacion.de("ACTUALIZAR_VERSION_SO", "version_sistema_operativo", idVersion)
                .valores(anterior, Map.of("version", limpia, "activo", activo)));
        return obtenerSistemaOperativo(idSo);
    }

    @Transactional(readOnly = true)
    public VersionSistemaOperativo obtenerVersion(Integer id) {
        return versiones.findConSistemaById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la versión de sistema operativo", id));
    }

    // ------------------------------------------------------------- utilidades

    private static DatosNivelCriticidad normalizar(DatosNivelCriticidad d) {
        return new DatosNivelCriticidad(d.nombre().trim(), d.prioridad(), d.frecuenciaRevisionDias(),
                d.frecuenciaMantenimientoDias(), d.plazoAutorizacionHoras(), d.plazoValidacionHoras());
    }

    private static Map<String, Object> instantanea(Entorno e) {
        Map<String, Object> valores = new HashMap<>();
        valores.put("nombre", e.getNombre());
        valores.put("descripcion", e.getDescripcion());
        valores.put("activo", e.isActivo());
        return valores;
    }

    private static Map<String, Object> instantanea(NivelCriticidad n) {
        return Map.of("nombre", n.getNombre(), "prioridad", n.getPrioridad(),
                "frecuenciaRevisionDias", n.getFrecuenciaRevisionDias(),
                "frecuenciaMantenimientoDias", n.getFrecuenciaMantenimientoDias(),
                "plazoAutorizacionHoras", n.getPlazoAutorizacionHoras(),
                "plazoValidacionHoras", n.getPlazoValidacionHoras(), "activo", n.isActivo());
    }
}
