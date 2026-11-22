package com.komainos.mantenimiento.service;

import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.mantenimiento.model.ActividadEjecucion;
import com.komainos.mantenimiento.model.EstadoActividad;
import com.komainos.mantenimiento.model.EstadoEvaluacion;
import com.komainos.mantenimiento.model.EstadoTareaMop;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.HistorialEstadoOrden;
import com.komainos.mantenimiento.model.MotivoCierre;
import com.komainos.mantenimiento.model.Incidencia;
import com.komainos.mantenimiento.model.Mop;
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
 * Actividad de una orden y de una ejecución del historial (DEC-59). Une, en el orden en que ocurrieron:
 * <ul>
 *   <li>los cambios de estado de la orden (su historial);</li>
 *   <li>los eventos registrados por cada ejecución remota: conexión, comprobación de acceso, análisis y tareas;</li>
 *   <li>las incidencias;</li>
 *   <li>para los análisis y las tareas anteriores al registro, su inicio y su fin, que ya guardaban.</li>
 * </ul>
 * Cada evento registrado tiene un solo origen y el servidor sale de él (DEC-60). El alcance es el de la ficha de la
 * orden: un responsable solo ve sus servidores.
 */
@Service
@RequiredArgsConstructor
public class ServicioConsultaActividad {

    private static final Map<OrigenIncidencia, String> ORIGEN = Map.of(
            OrigenIncidencia.PRUEBA_PILOTO, "Prueba piloto o condiciones de inicio",
            OrigenIncidencia.FALLO_EJECUCION, "Fallo de ejecución",
            OrigenIncidencia.TIEMPO_EXCEDIDO, "Tiempo excedido",
            OrigenIncidencia.VALIDACION_TECNICA_NO_CONFORME, "Validación técnica no conforme",
            OrigenIncidencia.RECHAZO_FUNCIONAL, "Rechazo funcional");

    private final ServicioConsultaAutomatizacion consulta;

    private final ActividadEjecucionRepositorio actividades;

    /** De la más reciente a la más antigua, como un registro */
    @Transactional(readOnly = true)
    public List<Evento> deOrden(Integer idOrden, AlcanceUsuario alcance) {
        ServicioConsultaAutomatizacion.FichaAutomatizacion ficha = consulta.ficha(idOrden, alcance);
        // Los orígenes que la ficha ya cargó con el alcance del usuario, con el servidor de cada uno
        Map<Integer, String> hostPorDetalle = new HashMap<>();
        Map<Integer, String> hostPorEvaluacion = new HashMap<>();
        Map<Integer, String> hostPorTarea = new HashMap<>();
        for (Mop m : ficha.mops()) {
            String host = m.getDetalle().getServidor().getHostname();
            hostPorDetalle.put(m.getDetalle().getId(), host);
            m.getTareas().forEach(t -> hostPorTarea.put(t.getId(), host));
        }
        for (Evaluacion e : ficha.evaluaciones()) {
            hostPorEvaluacion.put(e.getId(), e.getServidor().getHostname());
            if (e.getDetalle() != null) {
                hostPorDetalle.put(e.getDetalle().getId(), e.getServidor().getHostname());
            }
        }

        List<Evento> eventos = new ArrayList<>();
        for (HistorialEstadoOrden h : ficha.orden().getHistorial()) {
            Hito hito = hito(h, ficha.orden().getMotivoCierre());
            if (hito != null) {
                eventos.add(new Evento("orden-" + h.getId(), h.getFechaHora(), hito.nivel(),
                        hito.nivel() == NivelActividad.ERROR ? EstadoActividad.FALLIDO : null,
                        hito.accion(), h.getMotivo(), null, null, null, null, null, null, null));
            }
        }

        List<ActividadEjecucion> registradas = new ArrayList<>();
        if (!hostPorDetalle.isEmpty()) {
            registradas.addAll(actividades.findByIdDetalleInOrderByFechaHoraAscIdAsc(hostPorDetalle.keySet()));
        }
        if (!hostPorEvaluacion.isEmpty()) {
            registradas.addAll(actividades.findByIdEvaluacionInOrderByFechaHoraAscIdAsc(hostPorEvaluacion.keySet()));
        }
        if (!hostPorTarea.isEmpty()) {
            registradas.addAll(actividades.findByIdTareaInOrderByFechaHoraAscIdAsc(hostPorTarea.keySet()));
        }
        Set<Integer> analisisRegistrados = new HashSet<>();
        Set<Integer> tareasRegistradas = new HashSet<>();
        for (ActividadEjecucion a : registradas) {
            String host;
            if (a.getIdEvaluacion() != null) {
                analisisRegistrados.add(a.getIdEvaluacion());
                host = hostPorEvaluacion.get(a.getIdEvaluacion());
            } else if (a.getIdTarea() != null) {
                tareasRegistradas.add(a.getIdTarea());
                host = hostPorTarea.get(a.getIdTarea());
            } else {
                host = hostPorDetalle.get(a.getIdDetalle());
            }
            eventos.add(evento(a, host));
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
            eventos.add(new Evento("incidencia-" + i.getId(), i.getFechaApertura(), NivelActividad.ERROR,
                    EstadoActividad.FALLIDO, "Incidencia registrada", ORIGEN.get(i.getOrigen()), i.getError(),
                    i.getDetalle().getServidor().getHostname(), null, null, null, null, null));
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
        // Los eventos del detalle son solo los pasos generales: los de análisis y tareas tienen su propio origen
        actividades.findByIdDetalleOrderByFechaHoraAscIdAsc(detalle.getId()).forEach(a -> eventos.add(evento(a, host)));
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
        return new Evento("actividad-" + a.getId(), a.getFechaHora(), a.getNivel(), a.getEstado(), a.getAccion(),
                a.getDetalle(), a.getError(), host, a.getScript(), a.getIdTarea(), a.getIdEvaluacion(), a.getId(),
                a.getTipoFallo());
    }

    private static List<Evento> deAnalisis(Evaluacion e) {
        TipoEvaluacion tipo = e.getTipo();
        String script = ServicioActividad.version(e);
        String host = e.getServidor().getHostname();
        List<Evento> eventos = new ArrayList<>();
        if (e.getFechaInicio() != null) {
            eventos.add(new Evento("analisis-" + e.getId() + "-inicio", e.getFechaInicio(), NivelActividad.INFO,
                    e.getEstado() == EstadoEvaluacion.EN_CURSO ? EstadoActividad.EN_CURSO : null,
                    ServicioActividad.operacion(tipo, e.getEstado() == EstadoEvaluacion.EN_CURSO ? "en curso" : "iniciada"),
                    null, null, host, script, null, e.getId(), null, null));
        }
        if (e.getEstado() == EstadoEvaluacion.FINALIZADA) {
            eventos.add(new Evento("analisis-" + e.getId() + "-fin", e.getFechaFin(), NivelActividad.INFO,
                    EstadoActividad.COMPLETADO, ServicioActividad.operacion(tipo, "completada"), null, null, host, script,
                    null, e.getId(), null, null));
        } else if (e.getEstado() == EstadoEvaluacion.FALLIDA) {
            eventos.add(new Evento("analisis-" + e.getId() + "-fin", e.getFechaFin(), NivelActividad.ERROR,
                    EstadoActividad.FALLIDO, ServicioActividad.operacion(tipo, "ha fallado"), null, e.getError(), host,
                    script, null, e.getId(), null, null));
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
            eventos.add(new Evento("tarea-" + t.getId() + "-inicio", t.getFechaInicio(), NivelActividad.INFO,
                    enCurso ? EstadoActividad.EN_CURSO : null, nombre + (enCurso ? " en curso" : " iniciada"), null,
                    null, host, script, t.getId(), null, null, null));
        }
        if (t.getFechaFin() != null && (t.getEstado() == EstadoTareaMop.FINALIZADA
                || t.getEstado() == EstadoTareaMop.FALLIDA)) {
            boolean correcta = t.getEstado() == EstadoTareaMop.FINALIZADA;
            eventos.add(new Evento("tarea-" + t.getId() + "-fin", t.getFechaFin(),
                    correcta ? NivelActividad.INFO : NivelActividad.ERROR,
                    correcta ? EstadoActividad.COMPLETADO : EstadoActividad.FALLIDO,
                    nombre + (correcta ? " completada" : " ha fallado"),
                    t.getCodigoRetorno() == null ? null : "Código de salida: " + t.getCodigoRetorno(),
                    correcta ? null : t.getError(), host, script, t.getId(), null, null, null));
        }
        return eventos;
    }

    /**
     * El cambio de estado que el registro muestra como hito; nulo si no se muestra. El cierre lleva su motivo (DEC-61) y
     * volver a PROGRAMADA desde otro estado es una reprogramación
     */
    private static Hito hito(HistorialEstadoOrden h, MotivoCierre motivo) {
        return switch (h.getEstadoNuevo()) {
            case PROGRAMADA -> new Hito(h.getEstadoAnterior() == null ? "Orden programada" : "Orden reprogramada",
                    NivelActividad.INFO);
            case PENDIENTE_AUTORIZACION -> new Hito("Autorización solicitada", NivelActividad.INFO);
            case AUTORIZADA -> new Hito("Orden autorizada", NivelActividad.INFO);
            case EN_COLA -> new Hito("Orden encolada", NivelActividad.INFO);
            case EN_VALIDACION_TECNICA -> new Hito("Validación técnica iniciada", NivelActividad.INFO);
            case PENDIENTE_VALIDACION_FUNCIONAL -> new Hito("Validación funcional solicitada", NivelActividad.INFO);
            case CERRADA -> motivo == null ? new Hito("Orden cerrada", NivelActividad.INFO)
                    : new Hito("Orden cerrada: " + motivo.etiqueta().toLowerCase(),
                    motivo == MotivoCierre.SIN_IMPLEMENTACION ? NivelActividad.ERROR : NivelActividad.INFO);
            case EN_EVALUACION, EN_EJECUCION, INCIDENCIA -> null;
        };
    }

    private record Hito(String accion, NivelActividad nivel) {
    }

    /**
     * Un evento de la actividad, sin fase: se muestran en el orden en que ocurrieron (DEC-60).
     *
     * @param secuencia orden de registro entre eventos del mismo instante; nulo en los que no vienen del registro
     */
    public record Evento(String clave, Instant fecha, NivelActividad nivel, EstadoActividad estado, String accion,
                         String detalle, String error, String servidor, String script, Integer idTarea,
                         Integer idEvaluacion, Integer secuencia, TipoFallo tipoFallo) {
    }
}
