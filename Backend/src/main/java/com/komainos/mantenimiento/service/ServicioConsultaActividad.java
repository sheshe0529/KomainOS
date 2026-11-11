package com.komainos.mantenimiento.service;

import com.komainos.mantenimiento.model.ActividadEjecucion;
import com.komainos.mantenimiento.model.EstadoActividad;
import com.komainos.mantenimiento.model.EstadoEvaluacion;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.EstadoTareaMop;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.FaseActividad;
import com.komainos.mantenimiento.model.HistorialEstadoOrden;
import com.komainos.mantenimiento.model.Incidencia;
import com.komainos.mantenimiento.model.MopTarea;
import com.komainos.mantenimiento.model.NivelActividad;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.OrigenIncidencia;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.repository.ActividadEjecucionRepositorio;
import com.komainos.seguridad.model.AlcanceUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Actividad de una orden y de una ejecución del historial (DEC-59). Une, en orden cronológico:
 * <ul>
 *   <li>los cambios de estado de la orden (su historial);</li>
 *   <li>los eventos registrados por cada ejecución remota: conexión, comprobación de acceso, análisis y tareas;</li>
 *   <li>las incidencias;</li>
 *   <li>para los análisis y las tareas anteriores al registro, su inicio y su fin, que ya guardaban.</li>
 * </ul>
 * El alcance es el de la ficha de la orden: un responsable solo ve sus servidores.
 */
@Service
@RequiredArgsConstructor
public class ServicioConsultaActividad {

    private static final Map<EstadoOrden, Hito> HITOS = new HashMap<>();
    private static final Map<OrigenIncidencia, String> ORIGEN = Map.of(
            OrigenIncidencia.PRUEBA_PILOTO, "Prueba piloto o condiciones de inicio",
            OrigenIncidencia.FALLO_EJECUCION, "Fallo de ejecución",
            OrigenIncidencia.TIEMPO_EXCEDIDO, "Tiempo excedido",
            OrigenIncidencia.VALIDACION_TECNICA_NO_CONFORME, "Validación técnica no conforme",
            OrigenIncidencia.RECHAZO_FUNCIONAL, "Rechazo funcional");

    static {
        HITOS.put(EstadoOrden.PROGRAMADA, new Hito("Orden programada", NivelActividad.INFO));
        HITOS.put(EstadoOrden.PENDIENTE_AUTORIZACION, new Hito("Autorización solicitada", NivelActividad.INFO));
        HITOS.put(EstadoOrden.AUTORIZADA, new Hito("Orden autorizada", NivelActividad.INFO));
        HITOS.put(EstadoOrden.EN_COLA, new Hito("Orden encolada", NivelActividad.INFO));
        HITOS.put(EstadoOrden.PENDIENTE_VALIDACION, new Hito("Validación funcional solicitada", NivelActividad.INFO));
        HITOS.put(EstadoOrden.SIN_IMPLEMENTACION, new Hito("Ejecución bloqueada", NivelActividad.ERROR));
        HITOS.put(EstadoOrden.REPROGRAMADA, new Hito("Orden reprogramada", NivelActividad.INFO));
        HITOS.put(EstadoOrden.RECHAZADA, new Hito("Autorización rechazada", NivelActividad.INFO));
        HITOS.put(EstadoOrden.AUTORIZACION_VENCIDA, new Hito("Autorización vencida", NivelActividad.INFO));
        HITOS.put(EstadoOrden.VALIDACION_VENCIDA, new Hito("Validación funcional vencida", NivelActividad.INFO));
        HITOS.put(EstadoOrden.CANCELADA, new Hito("Orden cancelada", NivelActividad.INFO));
        HITOS.put(EstadoOrden.CERRADA, new Hito("Orden cerrada", NivelActividad.INFO));
    }

    private final ServicioConsultaAutomatizacion consulta;

    private final ActividadEjecucionRepositorio actividades;

    /** De la más reciente a la más antigua, como un registro */
    @Transactional(readOnly = true)
    public List<Evento> deOrden(Integer idOrden, AlcanceUsuario alcance) {
        ServicioConsultaAutomatizacion.FichaAutomatizacion ficha = consulta.ficha(idOrden, alcance);
        Map<Integer, String> hosts = new HashMap<>();
        Set<Integer> visibles = new HashSet<>();
        ficha.mops().forEach(m -> visibles.add(m.getDetalle().getId()));
        ficha.evaluaciones().forEach(e -> {
            if (e.getDetalle() != null) {
                visibles.add(e.getDetalle().getId());
            }
        });
        for (OrdenDetalle d : ficha.orden().getDetalles()) {
            hosts.put(d.getServidor().getId(), d.getServidor().getHostname());
        }

        List<Evento> eventos = new ArrayList<>();
        for (HistorialEstadoOrden h : ficha.orden().getHistorial()) {
            Hito hito = HITOS.get(h.getEstadoNuevo());
            if (hito != null) {
                eventos.add(new Evento("orden-" + h.getId(), h.getFechaHora(),
                        h.getEstadoNuevo() == EstadoOrden.PENDIENTE_VALIDACION ? FaseActividad.VALIDACION
                                : FaseActividad.ORDEN,
                        hito.nivel(), hito.nivel() == NivelActividad.ERROR ? EstadoActividad.FALLIDO : null,
                        hito.accion(), h.getMotivo(), null, null, null, null, null, null));
            }
        }

        List<ActividadEjecucion> registradas = visibles.isEmpty() ? List.of()
                : actividades.findByIdDetalleInOrderByFechaHoraAscIdAsc(visibles);
        Set<Integer> analisisRegistrados = new HashSet<>();
        Set<Integer> tareasRegistradas = new HashSet<>();
        for (ActividadEjecucion a : registradas) {
            eventos.add(evento(a, hosts.get(a.getIdServidor())));
            if (a.getIdEvaluacion() != null) {
                analisisRegistrados.add(a.getIdEvaluacion());
            }
            if (a.getIdTarea() != null) {
                tareasRegistradas.add(a.getIdTarea());
            }
        }
        // Lo ejecutado antes del registro de actividad: inicio y fin que guardan el análisis y la tarea
        for (Evaluacion e : ficha.evaluaciones()) {
            if (!analisisRegistrados.contains(e.getId())) {
                eventos.addAll(deAnalisis(e));
            }
        }
        ficha.mops().forEach(m -> m.getTareas().stream()
                .filter(t -> !tareasRegistradas.contains(t.getId()))
                .forEach(t -> eventos.addAll(deTarea(t, m.getDetalle().getServidor().getHostname()))));

        for (Incidencia i : ficha.incidencias()) {
            eventos.add(new Evento("incidencia-" + i.getId(), i.getFechaApertura(),
                    i.getOrigen() == OrigenIncidencia.VALIDACION_TECNICA_NO_CONFORME
                            || i.getOrigen() == OrigenIncidencia.RECHAZO_FUNCIONAL
                            ? FaseActividad.VALIDACION : FaseActividad.EJECUCION,
                    NivelActividad.ERROR, EstadoActividad.FALLIDO, "Incidencia registrada", ORIGEN.get(i.getOrigen()),
                    i.getError(), i.getDetalle().getServidor().getHostname(), null, null, null, null));
        }
        eventos.sort(CRONOLOGICO.reversed());
        return eventos;
    }

    /**
     * La ejecución de una tarea del historial (R3.4) con lo que la precedió en ese servidor: el inicio de la ejecución y
     * la comprobación de acceso. En orden cronológico
     */
    @Transactional(readOnly = true)
    public List<Evento> deEjecucion(Integer idTarea) {
        MopTarea tarea = consulta.ejecucion(idTarea);
        OrdenDetalle detalle = tarea.getMop().getDetalle();
        String host = detalle.getServidor().getHostname();
        List<Evento> eventos = new ArrayList<>();
        actividades.findByIdDetalleAndIdTareaIsNullAndFaseOrderByFechaHoraAscIdAsc(detalle.getId(),
                FaseActividad.EJECUCION).forEach(a -> eventos.add(evento(a, host)));
        List<ActividadEjecucion> propias = actividades.findByIdTareaOrderByFechaHoraAscIdAsc(idTarea);
        if (propias.isEmpty()) {
            eventos.addAll(deTarea(tarea, host));
        } else {
            propias.forEach(a -> eventos.add(evento(a, host)));
        }
        eventos.sort(CRONOLOGICO);
        return eventos;
    }

    private static final Comparator<Evento> CRONOLOGICO = Comparator
            .comparing(Evento::fecha, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Evento::secuencia, Comparator.nullsFirst(Comparator.naturalOrder()));

    private static Evento evento(ActividadEjecucion a, String host) {
        return new Evento("actividad-" + a.getId(), a.getFechaHora(), a.getFase(), a.getNivel(), a.getEstado(),
                a.getAccion(), a.getDetalle(), a.getError(), host, a.getScript(), a.getIdTarea(), a.getIdEvaluacion(),
                a.getId());
    }

    private static List<Evento> deAnalisis(Evaluacion e) {
        TipoEvaluacion tipo = e.getTipo();
        FaseActividad fase = ServicioActividad.fase(e.getTipo());
        String script = ServicioActividad.version(e);
        String host = e.getServidor().getHostname();
        List<Evento> eventos = new ArrayList<>();
        if (e.getFechaInicio() != null) {
            eventos.add(new Evento("analisis-" + e.getId() + "-inicio", e.getFechaInicio(), fase, NivelActividad.INFO,
                    e.getEstado() == EstadoEvaluacion.EN_CURSO ? EstadoActividad.EN_CURSO : null,
                    ServicioActividad.operacion(tipo, e.getEstado() == EstadoEvaluacion.EN_CURSO ? "en curso" : "iniciada"),
                    null, null, host, script, null, e.getId(), null));
        }
        if (e.getEstado() == EstadoEvaluacion.FINALIZADA) {
            eventos.add(new Evento("analisis-" + e.getId() + "-fin", e.getFechaFin(), fase, NivelActividad.INFO,
                    EstadoActividad.COMPLETADO, ServicioActividad.operacion(tipo, "completada"), null, null, host, script, null,
                    e.getId(), null));
        } else if (e.getEstado() == EstadoEvaluacion.FALLIDA) {
            eventos.add(new Evento("analisis-" + e.getId() + "-fin", e.getFechaFin(), fase, NivelActividad.ERROR,
                    EstadoActividad.FALLIDO, ServicioActividad.operacion(tipo, "ha fallado"), null, e.getError(), host, script, null,
                    e.getId(), null));
        }
        return eventos;
    }

    private static List<Evento> deTarea(MopTarea t, String host) {
        String nombre = "Tarea «%s»".formatted(t.getTarea().getNombre());
        String script = t.getVersion() == null ? null
                : "%s v%d".formatted(t.getVersion().getImplementacion().getScript().getNombre(),
                t.getVersion().getNumeroVersion());
        List<Evento> eventos = new ArrayList<>();
        if (t.getFechaInicio() != null) {
            boolean enCurso = t.getEstado() == EstadoTareaMop.EN_EJECUCION;
            eventos.add(new Evento("tarea-" + t.getId() + "-inicio", t.getFechaInicio(), FaseActividad.EJECUCION,
                    NivelActividad.INFO, enCurso ? EstadoActividad.EN_CURSO : null,
                    nombre + (enCurso ? " en curso" : " iniciada"), null, null, host, script, t.getId(), null, null));
        }
        if (t.getFechaFin() != null && (t.getEstado() == EstadoTareaMop.FINALIZADA
                || t.getEstado() == EstadoTareaMop.FALLIDA)) {
            boolean correcta = t.getEstado() == EstadoTareaMop.FINALIZADA;
            eventos.add(new Evento("tarea-" + t.getId() + "-fin", t.getFechaFin(), FaseActividad.EJECUCION,
                    correcta ? NivelActividad.INFO : NivelActividad.ERROR,
                    correcta ? EstadoActividad.COMPLETADO : EstadoActividad.FALLIDO,
                    nombre + (correcta ? " completada" : " ha fallado"),
                    t.getCodigoRetorno() == null ? null : "Código de salida " + t.getCodigoRetorno(),
                    correcta ? null : t.getError(), host, script, t.getId(), null, null));
        }
        return eventos;
    }

    private record Hito(String accion, NivelActividad nivel) {
    }

    /**
     * Un evento de la actividad.
     *
     * @param secuencia orden de registro entre eventos del mismo instante; nulo en los que no vienen del registro
     */
    public record Evento(String clave, Instant fecha, FaseActividad fase, NivelActividad nivel, EstadoActividad estado,
                         String accion, String detalle, String error, String servidor, String script, Integer idTarea,
                         Integer idEvaluacion, Integer secuencia) {
    }
}
