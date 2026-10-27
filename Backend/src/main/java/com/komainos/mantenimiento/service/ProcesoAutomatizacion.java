package com.komainos.mantenimiento.service;

import com.komainos.alerta.model.TipoAlerta;
import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Paralelo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Proceso programado del ciclo (EA02-EA06): vence plazos, despacha la cola, inicia las evaluaciones previas que llegaron
 * a su fecha y las revisiones periódicas pendientes. Los análisis corren en paralelo dentro del límite de conexiones.
 * Un problema de configuración se avisa una sola vez mientras no cambie, para no repetir la misma alerta cada ciclo.
 */
@Service
@Slf4j
public class ProcesoAutomatizacion {

    private static final int LIMITE_POR_CICLO = 50;
    private static final Actor EVALUACION = Actor.sistema("EVALUACION_PROGRAMADA");
    private static final Actor REVISION = Actor.sistema("REVISION_PERIODICA");

    private final PropiedadesAutomatizacion propiedades;
    private final ServicioAutorizaciones autorizaciones;
    private final ServicioValidaciones validaciones;
    private final ServicioCierreMantenimiento cierre;
    private final ServicioOrquestador orquestador;
    private final ServicioEvaluacionPrevia evaluacionPrevia;
    private final ServicioRevisionPeriodica revision;
    private final ServicioNotificaciones notificaciones;
    private final OrdenRepositorio ordenes;
    private final BloqueoAutomatizacion bloqueo;
    private final TransactionTemplate transaccion;
    private final AtomicBoolean ocupado = new AtomicBoolean();
    private final Map<String, String> ultimosAvisos = new ConcurrentHashMap<>();

    public ProcesoAutomatizacion(PropiedadesAutomatizacion propiedades, ServicioAutorizaciones autorizaciones,
                                 ServicioValidaciones validaciones, ServicioCierreMantenimiento cierre,
                                 ServicioOrquestador orquestador, ServicioEvaluacionPrevia evaluacionPrevia,
                                 ServicioRevisionPeriodica revision, ServicioNotificaciones notificaciones,
                                 OrdenRepositorio ordenes, BloqueoAutomatizacion bloqueo,
                                 PlatformTransactionManager transacciones) {
        this.propiedades = propiedades;
        this.autorizaciones = autorizaciones;
        this.validaciones = validaciones;
        this.cierre = cierre;
        this.orquestador = orquestador;
        this.evaluacionPrevia = evaluacionPrevia;
        this.revision = revision;
        this.notificaciones = notificaciones;
        this.ordenes = ordenes;
        this.bloqueo = bloqueo;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    @Scheduled(fixedDelayString = "${komainos.automatizacion.intervalo-ms:30000}",
            initialDelayString = "${komainos.automatizacion.intervalo-ms:30000}")
    public void programado() {
        if (propiedades.procesoHabilitado()) {
            procesar();
        }
    }

    /** Un ciclo a la vez: si ya hay uno en curso, el llamado no hace nada */
    public Resumen procesar() {
        if (!ocupado.compareAndSet(false, true)) {
            return new Resumen(false, 0, 0, 0, 0);
        }
        try {
            int vencidas = autorizaciones.vencer() + validaciones.vencer() + transaccion.execute(estado -> {
                bloqueo.tomar();
                return cierre.cerrarCanceladas();
            });
            int despachadas = orquestador.despachar();

            AtomicInteger evaluadas = new AtomicInteger();
            Paralelo.paraCada(evaluacionPrevia.pendientes(LIMITE_POR_CICLO), id -> {
                try {
                    evaluacionPrevia.evaluar(id, EVALUACION);
                    evaluadas.incrementAndGet();
                    ultimosAvisos.remove("orden:" + id);
                } catch (ReglaNegocioException ex) {
                    avisarOrden(id, ex.getMessage());
                }
            }).forEach(ex -> log.error("Error inesperado en una evaluación programada", ex));

            AtomicInteger revisadas = new AtomicInteger();
            Paralelo.paraCada(revision.pendientes(LIMITE_POR_CICLO), id -> {
                try {
                    revision.revisar(id, REVISION);
                    revisadas.incrementAndGet();
                    ultimosAvisos.remove("servidor:" + id);
                } catch (ReglaNegocioException ex) {
                    avisarUnaVez("servidor:" + id, "Revisión del servidor %d pendiente: %s".formatted(id, ex.getMessage()));
                }
            }).forEach(ex -> log.error("Error inesperado en una revisión periódica", ex));

            return new Resumen(true, vencidas, despachadas, evaluadas.get(), revisadas.get());
        } finally {
            ocupado.set(false);
        }
    }

    /** La orden sigue programada: el administrador recibe el motivo una vez y el proceso reintenta cada ciclo */
    private void avisarOrden(Integer idOrden, String motivo) {
        if (!avisarUnaVez("orden:" + idOrden, "Evaluación de la orden %d pendiente: %s".formatted(idOrden, motivo))) {
            return;
        }
        transaccion.executeWithoutResult(estado -> ordenes.findConDetalleById(idOrden).ifPresent(orden ->
                notificaciones.aOrden(orden, TipoAlerta.SIN_IMPLEMENTACION,
                        "No se pudo iniciar la evaluación de " + orden.getCodigo(), motivo,
                        notificaciones.administradores())));
    }

    private boolean avisarUnaVez(String clave, String mensaje) {
        String anterior = ultimosAvisos.put(clave, mensaje);
        if (mensaje.equals(anterior)) {
            log.debug(mensaje);
            return false;
        }
        log.warn(mensaje);
        return true;
    }

    /** Lo que hizo el ciclo, para el aviso del panel */
    public record Resumen(boolean ejecutado, int vencidas, int despachadas, int evaluadas, int revisadas) {

        public List<String> descripcion() {
            if (!ejecutado) {
                return List.of("Ya hay un procesamiento en curso; intente de nuevo en unos segundos");
            }
            return List.of("%d orden(es) evaluada(s)".formatted(evaluadas),
                    "%d revisión(es) periódica(s)".formatted(revisadas),
                    "%d servidor(es) iniciado(s) desde la cola".formatted(despachadas),
                    "%d plazo(s) vencido(s) o cancelación(es) cerradas".formatted(vencidas));
        }
    }
}
