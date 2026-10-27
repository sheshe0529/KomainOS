package com.komainos.mantenimiento.service;

import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoEvaluacion;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.EstadoTareaMop;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.MopTarea;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.OrigenIncidencia;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.mantenimiento.repository.MopTareaRepositorio;
import com.komainos.mantenimiento.repository.OrdenDetalleRepositorio;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Al arrancar, nada se está ejecutando: lo que quedó en curso por un reinicio no tiene resultado conocido. Los análisis
 * se registran como fallidos, las tareas y servidores en ejecución como fallidos con su incidencia, y las órdenes
 * continúan desde el paso en que quedaron. Nunca se reintenta una tarea: no se sabe si llegó a cambiar el servidor.
 */
@Service
@Slf4j
public class ServicioRecuperacion {

    private static final String MOTIVO = "se interrumpió por un reinicio del sistema";
    private static final Actor SISTEMA = Actor.sistema("RECUPERACION");

    private final EvaluacionRepositorio evaluacionesRepositorio;
    private final MopTareaRepositorio tareas;
    private final OrdenDetalleRepositorio detalles;
    private final OrdenRepositorio ordenes;
    private final ServicioEvaluaciones evaluaciones;
    private final ServicioIncidencias incidencias;
    private final ServicioEvaluacionPrevia evaluacionPrevia;
    private final ServicioValidaciones validaciones;
    private final BloqueoAutomatizacion bloqueo;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public ServicioRecuperacion(EvaluacionRepositorio evaluacionesRepositorio, MopTareaRepositorio tareas,
                                OrdenDetalleRepositorio detalles, OrdenRepositorio ordenes,
                                ServicioEvaluaciones evaluaciones, ServicioIncidencias incidencias,
                                ServicioEvaluacionPrevia evaluacionPrevia, ServicioValidaciones validaciones,
                                BloqueoAutomatizacion bloqueo, Clock reloj, PlatformTransactionManager transacciones) {
        this.evaluacionesRepositorio = evaluacionesRepositorio;
        this.tareas = tareas;
        this.detalles = detalles;
        this.ordenes = ordenes;
        this.evaluaciones = evaluaciones;
        this.incidencias = incidencias;
        this.evaluacionPrevia = evaluacionPrevia;
        this.validaciones = validaciones;
        this.bloqueo = bloqueo;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void alArrancar() {
        try {
            recuperar();
        } catch (RuntimeException ex) {
            log.error("No se pudo recuperar el estado de la automatización al arrancar", ex);
        }
    }

    public void recuperar() {
        int analisis = transaccion.execute(estado -> {
            bloqueo.tomar();
            List<Evaluacion> enCurso = evaluacionesRepositorio.findByEstado(EstadoEvaluacion.EN_CURSO);
            enCurso.forEach(e -> evaluaciones.interrumpir(e.getId(), "El análisis " + MOTIVO));
            return enCurso.size();
        });
        int servidores = transaccion.execute(estado -> {
            bloqueo.tomar();
            Instant ahora = Tiempo.ahora(reloj);
            for (MopTarea tarea : tareas.findByEstado(EstadoTareaMop.EN_EJECUCION)) {
                tarea.interrumpir("La tarea " + MOTIVO + "; su resultado es desconocido", ahora);
            }
            List<OrdenDetalle> enEjecucion = detalles.findByEstado(EstadoDetalleOrden.EN_EJECUCION);
            for (OrdenDetalle detalle : enEjecucion) {
                detalle.cambiarEstado(EstadoDetalleOrden.FALLIDO, ahora);
                incidencias.registrar(detalle, OrigenIncidencia.FALLO_EJECUCION,
                        "La ejecución en %s %s; revise el servidor antes de cerrar la incidencia"
                                .formatted(detalle.getServidor().getHostname(), MOTIVO));
            }
            return enEjecucion.size();
        });
        List<Integer> enEvaluacion = transaccion.execute(estado -> ordenes
                .findByEstadoInOrderByFechaInicioProgramadaAsc(EnumSet.of(EstadoOrden.EN_EVALUACION)).stream()
                .map(Orden::getId).toList());
        enEvaluacion.forEach(id -> evaluacionPrevia.concluir(id, Map.of(), SISTEMA));
        List<Integer> enEjecucion = transaccion.execute(estado -> ordenes
                .findByEstadoInOrderByFechaInicioProgramadaAsc(EnumSet.of(EstadoOrden.EN_EJECUCION, EstadoOrden.INCIDENCIA))
                .stream().map(Orden::getId).toList());
        enEjecucion.forEach(validaciones::completarEjecucion);
        if (analisis + servidores + enEvaluacion.size() > 0) {
            log.warn("Recuperación al arrancar: {} análisis y {} servidores interrumpidos, {} evaluaciones retomadas",
                    analisis, servidores, enEvaluacion.size());
        }
    }
}
