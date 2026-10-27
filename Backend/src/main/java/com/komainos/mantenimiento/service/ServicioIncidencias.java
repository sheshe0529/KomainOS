package com.komainos.mantenimiento.service;

import com.komainos.alerta.model.TipoAlerta;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoIncidencia;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Incidencia;
import com.komainos.mantenimiento.model.Mop;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.OrigenIncidencia;
import com.komainos.mantenimiento.repository.IncidenciaRepositorio;
import com.komainos.mantenimiento.repository.MopRepositorio;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.repository.UsuarioRepositorio;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Incidencias del ciclo (RF59, RF61, RF62). Al registrarse una, la orden no inicia más tareas ni servidores: lo que
 * está en curso termina por su cuenta. Atenderla no reanuda nada; cuando todas están cerradas y no queda trabajo en
 * curso, la orden continúa a su cierre (HU28 CA3).
 */
@Service
@RequiredArgsConstructor
public class ServicioIncidencias {

    private final IncidenciaRepositorio incidencias;
    private final MopRepositorio mops;
    private final UsuarioRepositorio usuarios;
    private final ServicioNotificaciones notificaciones;
    private final ServicioCierreMantenimiento cierre;
    private final BloqueoAutomatizacion bloqueo;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;

    /**
     * RF59: deja la orden en incidencia, no inicia los servidores ni las tareas pendientes y conserva en el error el
     * estado de cada servidor en ese momento.
     */
    @Transactional
    public Incidencia registrar(OrdenDetalle detalle, OrigenIncidencia origen, String error) {
        Instant ahora = Tiempo.ahora(reloj);
        Orden orden = detalle.getOrden();
        if (orden.getEstado().puedePasarA(EstadoOrden.INCIDENCIA)) {
            orden.cambiarEstado(EstadoOrden.INCIDENCIA, null, recortar(error, 1000), ahora);
        }
        for (OrdenDetalle otro : orden.getDetalles()) {
            if (otro.getEstado() == EstadoDetalleOrden.EN_COLA || otro.getEstado() == EstadoDetalleOrden.PENDIENTE) {
                otro.omitirSiPendiente();
            }
        }
        mops.findByDetalleOrdenIdOrderByDetallePosicionEjecucionAsc(orden.getId()).forEach(Mop::omitirPendientes);

        String texto = error + (orden.esGrupal() ? ". Estado de los servidores: " + estados(orden) : "");
        Incidencia incidencia = incidencias.save(Incidencia.abrir(detalle, origen, texto, ahora));
        notificaciones.incidencia(detalle, incidencia.getId(),
                origen == OrigenIncidencia.TIEMPO_EXCEDIDO ? TipoAlerta.VENTANA_EXCEDIDA : TipoAlerta.INCIDENCIA_REGISTRADA,
                "Incidencia en %s · %s".formatted(orden.getCodigo(), detalle.getServidor().getHostname()),
                recortar(texto, 2000));
        return incidencia;
    }

    @Transactional
    public Incidencia atender(Integer id, DatosAtencion datos, Actor actor) {
        bloqueo.tomar();
        Incidencia incidencia = obtener(id);
        Usuario autor = actor.esSistema() ? null : usuarios.findById(actor.usuarioId()).orElse(null);
        incidencia.atender(autor, datos.diagnostico(), datos.acciones(), datos.resultado(), datos.cerrar(),
                Tiempo.ahora(reloj));
        Orden orden = incidencia.getDetalle().getOrden();
        auditoria.registrar(actor, Operacion.de(datos.cerrar() ? "CERRAR_INCIDENCIA" : "ATENDER_INCIDENCIA",
                "incidencia", id).sobreOrden(orden.getId()).sobreServidor(incidencia.getDetalle().getServidor().getId())
                .motivo(datos.resultado()));
        if (datos.cerrar()) {
            cerrarOrdenSiCorresponde(orden, "Incidencias atendidas: " + datos.resultado(), actor);
        }
        return incidencia;
    }

    /** La orden se cierra cuando no quedan incidencias abiertas ni servidores en cola o en ejecución */
    @Transactional
    public boolean cerrarOrdenSiCorresponde(Orden orden, String motivo, Actor actor) {
        if (orden.getEstado() != EstadoOrden.INCIDENCIA) {
            return false;
        }
        boolean abiertas = incidencias.findByDetalleOrdenIdOrderByFechaAperturaAsc(orden.getId()).stream()
                .anyMatch(i -> !i.estaCerrada());
        boolean enCurso = orden.getDetalles().stream().anyMatch(d -> d.getEstado() == EstadoDetalleOrden.EN_EJECUCION
                || d.getEstado() == EstadoDetalleOrden.EN_COLA);
        if (abiertas || enCurso) {
            return false;
        }
        cierre.cerrar(orden, recortar(motivo, 1000), actor);
        return true;
    }

    @Transactional(readOnly = true)
    public Incidencia obtener(Integer id) {
        Incidencia incidencia = incidencias.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la incidencia", id));
        Hibernate.initialize(incidencia.getDetalle().getOrden().getDetalles());
        return incidencia;
    }

    /** Solo los filtros presentes: un parámetro nulo sin tipo falla con los enumerados de PostgreSQL (DEC-26) */
    @Transactional(readOnly = true)
    public Page<Incidencia> listar(FiltroIncidencias filtro, Pageable pagina) {
        Specification<Incidencia> especificacion = (raiz, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro.estado() != null) {
                condiciones.add(cb.equal(raiz.get("estado"), filtro.estado()));
            }
            if (filtro.origen() != null) {
                condiciones.add(cb.equal(raiz.get("origen"), filtro.origen()));
            }
            if (filtro.idServidor() != null) {
                condiciones.add(cb.equal(raiz.get("detalle").get("servidor").get("id"), filtro.idServidor()));
            }
            if (filtro.texto() != null && !filtro.texto().isBlank()) {
                String patron = "%" + filtro.texto().trim().toLowerCase(Locale.ROOT) + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(raiz.get("detalle").get("servidor").get("hostname")), patron),
                        cb.like(cb.lower(raiz.get("detalle").get("orden").get("codigo")), patron)));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        return incidencias.findAll(especificacion, PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(),
                Sort.by(Sort.Order.desc("fechaApertura"), Sort.Order.desc("id"))));
    }

    private static String estados(Orden orden) {
        return orden.getDetalles().stream()
                .map(d -> "%s %s".formatted(d.getServidor().getHostname(), switch (d.getEstado()) {
                    case EN_EJECUCION -> "en ejecución (termina la tarea en curso)";
                    case FINALIZADO -> "finalizado";
                    case FALLIDO -> "fallido";
                    case NO_INICIADO -> "no iniciado";
                    case EN_COLA, PENDIENTE -> "pendiente";
                }))
                .collect(Collectors.joining(", "));
    }

    private static String recortar(String texto, int largo) {
        return texto == null || texto.length() <= largo ? texto : texto.substring(0, largo - 1) + "…";
    }

    public record DatosAtencion(String diagnostico, String acciones, String resultado, boolean cerrar) {
    }

    public record FiltroIncidencias(EstadoIncidencia estado, OrigenIncidencia origen,
                                    Integer idServidor, String texto) {
    }
}
