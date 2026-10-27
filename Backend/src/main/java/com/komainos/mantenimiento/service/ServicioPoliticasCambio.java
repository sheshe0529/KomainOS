package com.komainos.mantenimiento.service;

import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.inventario.model.Entorno;
import com.komainos.inventario.service.ServicioCatalogos;
import com.komainos.mantenimiento.model.NotificacionRegla;
import com.komainos.mantenimiento.model.ReglaPoliticaCambio;
import com.komainos.mantenimiento.model.TipoDestinatario;
import com.komainos.mantenimiento.repository.NotificacionReglaRepositorio;
import com.komainos.mantenimiento.repository.ReglaPoliticaCambioRepositorio;
import com.komainos.shared.exception.ConflictoException;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Concurrencia;
import com.komainos.shared.util.Tiempo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Política de gestión del cambio (RF43, HU24): una regla por entorno y afectación con las aprobaciones exigidas y los
 * roles a notificar. Cambiar una regla no altera las rondas de autorización ya abiertas.
 */
@Service
@RequiredArgsConstructor
public class ServicioPoliticasCambio {

    private final ReglaPoliticaCambioRepositorio reglas;
    private final NotificacionReglaRepositorio notificaciones;
    private final ServicioCatalogos catalogos;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;

    @Transactional(readOnly = true)
    public Page<ReglaPoliticaCambio> listar(Pageable pagina) {
        return reglas.findAll(pagina);
    }

    @Transactional(readOnly = true)
    public List<NotificacionRegla> notificaciones(Integer idRegla) {
        return notificaciones.findByIdRegla(idRegla);
    }

    @Transactional
    public ReglaPoliticaCambio guardar(Integer id, DatosRegla datos, Actor actor) {
        Entorno entorno = catalogos.obtenerEntorno(datos.idEntorno());
        if (!entorno.isActivo()) {
            throw new ReglaNegocioException("El entorno %s está inactivo".formatted(entorno.getNombre()));
        }
        reglas.findByEntornoIdAndAfectacion(datos.idEntorno(), datos.afectacion())
                .filter(existente -> !Objects.equals(existente.getId(), id))
                .ifPresent(existente -> {
                    throw new ConflictoException("Ya hay una regla para %s con afectación %s"
                            .formatted(entorno.getNombre(), datos.afectacion()));
                });
        ReglaPoliticaCambio regla = id == null
                ? ReglaPoliticaCambio.nueva(entorno, datos.afectacion())
                : reglas.findById(id).orElseThrow(() -> RecursoNoEncontradoException.de("la regla", id));
        if (id != null && (!regla.getEntorno().getId().equals(datos.idEntorno()) || regla.getAfectacion() != datos.afectacion())) {
            throw new ReglaNegocioException("El entorno y la afectación de una regla no cambian: edite la celda que corresponde");
        }
        Concurrencia.exigirVersion(datos.version(), regla.getFechaActualizacion(), "la regla de la política");
        Map<String, Object> anterior = id == null ? null : instantanea(regla, notificaciones(id));

        regla.configurar(datos.responsable(), datos.administrador(), datos.activo(), Tiempo.ahora(reloj));
        reglas.save(regla);
        notificaciones.deleteByIdRegla(regla.getId());
        notificaciones.flush();
        for (TipoDestinatario tipo : new TreeSet<>(datos.notificaciones())) {
            notificaciones.save(NotificacionRegla.de(regla.getId(), tipo));
        }
        auditoria.registrar(actor, Operacion.de(id == null ? "REGISTRAR_POLITICA_CAMBIO" : "ACTUALIZAR_POLITICA_CAMBIO",
                "regla_politica_cambio", regla.getId()).valores(anterior, instantanea(regla, notificaciones(regla.getId()))));
        return regla;
    }

    /** RF45: la regla activa para el entorno y la afectación de la orden */
    @Transactional(readOnly = true, noRollbackFor = ReglaNegocioException.class)
    public ReglaPoliticaCambio aplicable(Integer idEntorno, NivelAfectacion afectacion) {
        return reglas.findByEntornoIdAndAfectacion(idEntorno, afectacion)
                .filter(ReglaPoliticaCambio::isActivo)
                .orElseThrow(() -> new ReglaNegocioException(
                        "Complete la política de cambio del entorno para la afectación %s".formatted(afectacion)));
    }

    /**
     * HU24 CA5: un entorno necesita su política completa antes de ejecutar mantenimientos. Se comprueba antes de evaluar,
     * porque la afectación solo se conoce después de generar la MOP (RF45).
     */
    @Transactional(readOnly = true, noRollbackFor = ReglaNegocioException.class)
    public void exigirCompleta(Entorno entorno) {
        List<NivelAfectacion> faltantes = Arrays.stream(NivelAfectacion.values())
                .filter(nivel -> reglas.findByEntornoIdAndAfectacion(entorno.getId(), nivel)
                        .filter(ReglaPoliticaCambio::isActivo).isEmpty())
                .toList();
        if (!faltantes.isEmpty()) {
            throw new ReglaNegocioException("Complete la política de cambio del entorno %s: faltan las reglas de %s"
                    .formatted(entorno.getNombre(), String.join(", ", faltantes.stream().map(Enum::name).toList())));
        }
    }

    private static Map<String, Object> instantanea(ReglaPoliticaCambio r, List<NotificacionRegla> n) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("entorno", r.getEntorno().getNombre());
        v.put("afectacion", r.getAfectacion());
        v.put("requiereResponsable", r.isRequiereResponsable());
        v.put("requiereAdministrador", r.isRequiereAdministrador());
        v.put("activo", r.isActivo());
        v.put("notificaciones", n.stream().map(NotificacionRegla::getDestinatario).toList());
        return v;
    }

    public record DatosRegla(Integer idEntorno, NivelAfectacion afectacion, boolean responsable, boolean administrador,
                             boolean activo, Set<TipoDestinatario> notificaciones, Instant version) {
    }
}
