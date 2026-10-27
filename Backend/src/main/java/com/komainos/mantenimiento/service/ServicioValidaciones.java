package com.komainos.mantenimiento.service;

import com.komainos.alerta.model.TipoAlerta;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoEvaluacion;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.EvaluacionUmbral;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.OrigenIncidencia;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.model.ValidacionFuncional;
import com.komainos.mantenimiento.model.ValidacionTecnica;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.mantenimiento.repository.ValidacionFuncionalRepositorio;
import com.komainos.mantenimiento.repository.ValidacionTecnicaRepositorio;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.Usuario;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Después de ejecutar (RF54-RF57, EA06): análisis posterior de cada servidor finalizado, validación técnica según los
 * umbrales disparados y solicitud de la conformidad del responsable con el plazo de la criticidad. Una validación no
 * conforme, técnica o funcional, abre una incidencia; un plazo vencido cierra el ciclo sin presumir conformidad.
 */
@Service
@Slf4j
public class ServicioValidaciones {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Lima"));
    private static final Actor SISTEMA = Actor.sistema("VALIDACION_TECNICA");

    private final OrdenRepositorio ordenes;
    private final EvaluacionRepositorio evaluacionesRepositorio;
    private final ValidacionTecnicaRepositorio tecnicas;
    private final ValidacionFuncionalRepositorio funcionales;
    private final ServicioConsultaOrdenes consulta;
    private final ServicioEvaluaciones evaluaciones;
    private final EjecutorAnalisis analisis;
    private final ServicioIncidencias incidencias;
    private final ServicioCierreMantenimiento cierre;
    private final ServicioNotificaciones notificaciones;
    private final BloqueoAutomatizacion bloqueo;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public ServicioValidaciones(OrdenRepositorio ordenes, EvaluacionRepositorio evaluacionesRepositorio,
                                ValidacionTecnicaRepositorio tecnicas, ValidacionFuncionalRepositorio funcionales,
                                ServicioConsultaOrdenes consulta, ServicioEvaluaciones evaluaciones,
                                EjecutorAnalisis analisis, ServicioIncidencias incidencias,
                                ServicioCierreMantenimiento cierre, ServicioNotificaciones notificaciones,
                                BloqueoAutomatizacion bloqueo, ServicioAuditoria auditoria, Clock reloj,
                                PlatformTransactionManager transacciones) {
        this.ordenes = ordenes;
        this.evaluacionesRepositorio = evaluacionesRepositorio;
        this.tecnicas = tecnicas;
        this.funcionales = funcionales;
        this.consulta = consulta;
        this.evaluaciones = evaluaciones;
        this.analisis = analisis;
        this.incidencias = incidencias;
        this.cierre = cierre;
        this.notificaciones = notificaciones;
        this.bloqueo = bloqueo;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /**
     * Lo llama cada servidor al terminar su ejecución; solo actúa cuando ya no queda ninguno en cola ni en ejecución.
     * El bloqueo garantiza que un solo hilo inicie los análisis posteriores; los demás ven que ya existen y salen.
     */
    public void completarEjecucion(Integer idOrden) {
        Paso paso = transaccion.execute(estado -> prepararValidacion(idOrden));
        if (paso == null || paso.accion() == Accion.NADA) {
            return;
        }
        if (paso.accion() == Accion.ANALIZAR) {
            analisis.ejecutarTodas(paso.evaluaciones());
        }
        transaccion.executeWithoutResult(estado -> validarTecnicamente(idOrden, paso.problemas()));
    }

    /** RF57: el responsable registra su conformidad o su rechazo dentro del plazo */
    @Transactional
    public void validar(Integer idOrden, boolean conforme, String observaciones, AlcanceUsuario alcance) {
        bloqueo.tomar();
        Orden orden = consulta.obtener(idOrden, alcance);
        if (orden.getEstado() != EstadoOrden.PENDIENTE_VALIDACION) {
            throw new ReglaNegocioException("La orden %s no espera validación funcional".formatted(orden.getCodigo()));
        }
        ValidacionFuncional validacion = funcionales.findByOrdenId(idOrden)
                .filter(v -> v.getResponsable().getId().equals(alcance.usuarioId()))
                .orElseThrow(() -> new ReglaNegocioException("La validación corresponde al responsable de los servidores"));
        validacion.responder(conforme, observaciones, Tiempo.ahora(reloj));
        auditoria.registrar(alcance.actor(), Operacion.de(conforme ? "VALIDAR_CONFORME" : "VALIDAR_NO_CONFORME",
                "validacion_funcional", validacion.getId()).sobreOrden(idOrden).motivo(observaciones));
        if (conforme) {
            notificaciones.aOrden(orden, TipoAlerta.CIERRE_MANTENIMIENTO, orden.getCodigo() + " validado",
                    "El responsable confirmó la conformidad: " + observaciones, notificaciones.administradores());
            cierre.cerrar(orden, "Validaciones técnica y funcional conformes", alcance.actor());
            return;
        }
        // CP147: el rechazo deriva una incidencia por cada servidor intervenido
        for (OrdenDetalle detalle : orden.getDetalles()) {
            if (detalle.getEstado() == EstadoDetalleOrden.FINALIZADO) {
                incidencias.registrar(detalle, OrigenIncidencia.RECHAZO_FUNCIONAL,
                        "El responsable rechazó el resultado: " + observaciones);
            }
        }
    }

    /** RF57, EA06: sin respuesta dentro del plazo se registra vencida, se avisa y se cierra sin presumir conformidad */
    @Transactional
    public int vencer() {
        bloqueo.tomar();
        Instant ahora = Tiempo.ahora(reloj);
        int vencidas = 0;
        for (Orden orden : ordenes.findByEstadoInOrderByFechaInicioProgramadaAsc(EnumSet.of(EstadoOrden.PENDIENTE_VALIDACION))) {
            Optional<ValidacionFuncional> validacion = funcionales.findByOrdenId(orden.getId());
            if (validacion.isEmpty() || !validacion.get().estaPendiente() || !validacion.get().vencida(ahora)) {
                continue;
            }
            validacion.get().vencer(ahora);
            orden.cambiarEstado(EstadoOrden.VALIDACION_VENCIDA, null,
                    "Plazo de validación funcional vencido; no se presume conformidad", ahora);
            notificaciones.aOrden(orden, TipoAlerta.VALIDACION_VENCIDA, "Validación vencida: " + orden.getCodigo(),
                    "El responsable no respondió antes del %s; el ciclo se cierra sin conformidad."
                            .formatted(FECHA.format(validacion.get().getFechaLimite())),
                    notificaciones.involucrados(orden));
            cierre.cerrar(orden, "Validación funcional vencida sin respuesta", Actor.sistema("VENCIMIENTO_VALIDACION"));
            vencidas++;
        }
        return vencidas;
    }

    private Paso prepararValidacion(Integer idOrden) {
        bloqueo.tomar();
        Orden orden = ordenes.findConDetalleById(idOrden).orElseThrow();
        boolean enCurso = orden.getDetalles().stream().anyMatch(d -> !d.estaTerminado());
        if (enCurso) {
            return Paso.nada();
        }
        if (orden.getEstado() == EstadoOrden.INCIDENCIA) {
            incidencias.cerrarOrdenSiCorresponde(orden, "Incidencias atendidas y ejecución terminada", SISTEMA);
            return Paso.nada();
        }
        if (orden.getEstado() != EstadoOrden.EN_EJECUCION) {
            return Paso.nada();
        }
        List<Evaluacion> posteriores = evaluacionesRepositorio.deOrden(idOrden, TipoEvaluacion.POSTERIOR);
        if (!posteriores.isEmpty()) {
            boolean pendientes = posteriores.stream().anyMatch(e -> e.getEstado() == EstadoEvaluacion.EN_CURSO);
            return pendientes ? Paso.nada() : new Paso(Accion.CONCLUIR, List.of(), Map.of());
        }
        List<Integer> iniciadas = new ArrayList<>();
        Map<Integer, String> problemas = new LinkedHashMap<>();
        for (OrdenDetalle detalle : orden.getDetalles()) {
            if (detalle.getEstado() != EstadoDetalleOrden.FINALIZADO) {
                continue;
            }
            try {
                iniciadas.add(evaluaciones.iniciar(detalle.getServidor(), detalle, TipoEvaluacion.POSTERIOR,
                        orden.getIdCuentaServicio()).getId());
            } catch (ReglaNegocioException ex) {
                problemas.put(detalle.getId(), ex.getMessage());
            }
        }
        return new Paso(Accion.ANALIZAR, iniciadas, problemas);
    }

    /** RF55: conforme solo si el análisis posterior terminó y ningún umbral quedó en advertencia o crítico */
    private void validarTecnicamente(Integer idOrden, Map<Integer, String> problemas) {
        bloqueo.tomar();
        Orden orden = ordenes.findConDetalleById(idOrden).orElseThrow();
        if (orden.getEstado() != EstadoOrden.EN_EJECUCION) {
            return;
        }
        Instant ahora = Tiempo.ahora(reloj);
        List<OrdenDetalle> finalizados = orden.getDetalles().stream()
                .filter(d -> d.getEstado() == EstadoDetalleOrden.FINALIZADO)
                .toList();
        if (finalizados.isEmpty()) {
            incidencias.registrar(orden.getDetalles().getFirst(), OrigenIncidencia.FALLO_EJECUCION,
                    "Ningún servidor de la orden completó su ejecución");
            return;
        }
        for (OrdenDetalle detalle : finalizados) {
            if (tecnicas.existsByDetalleId(detalle.getId())) {
                continue;
            }
            Optional<Evaluacion> posterior = evaluacionesRepositorio.findByDetalleIdOrderByFechaInicioAsc(detalle.getId())
                    .stream().filter(e -> e.getTipo() == TipoEvaluacion.POSTERIOR).reduce((a, b) -> b);
            String observaciones = observaciones(posterior, problemas.get(detalle.getId()));
            boolean conforme = posterior.isPresent() && posterior.get().estaFinalizada()
                    && posterior.get().umbralesActivados().isEmpty();
            tecnicas.save(ValidacionTecnica.registrar(detalle, conforme, observaciones, ahora));
            if (!conforme) {
                incidencias.registrar(detalle, OrigenIncidencia.VALIDACION_TECNICA_NO_CONFORME,
                        "Validación técnica no conforme en %s: %s".formatted(detalle.getServidor().getHostname(),
                                observaciones));
            }
        }
        if (orden.getEstado() != EstadoOrden.EN_EJECUCION) {
            return;
        }
        orden.cambiarEstado(EstadoOrden.PENDIENTE_VALIDACION, null,
                "Validación técnica conforme; pendiente de la conformidad del responsable", ahora);
        // RF56: un grupo tiene un único responsable (RF20), así la orden tiene una sola validación funcional
        Usuario responsable = finalizados.getFirst().getServidor().getResponsable();
        Instant limite = ahora.plus(Duration.ofHours(orden.getNivelCriticidad().getPlazoValidacionHoras()));
        funcionales.save(ValidacionFuncional.solicitar(orden, responsable, ahora, limite));
        notificaciones.aOrden(orden, TipoAlerta.SOLICITUD_VALIDACION, "Validación funcional pendiente: " + orden.getCodigo(),
                "Revise el reporte comparativo y registre su conformidad antes del %s.".formatted(FECHA.format(limite)),
                List.of(responsable.getId()));
    }

    private static String observaciones(Optional<Evaluacion> posterior, String problema) {
        if (posterior.isEmpty()) {
            return "No se pudo ejecutar el análisis posterior" + (problema == null ? "" : ": " + problema);
        }
        Evaluacion e = posterior.get();
        if (!e.estaFinalizada()) {
            return "El análisis posterior no fue concluyente: " + e.getError();
        }
        String fuera = e.getMediciones().stream()
                .flatMap(m -> m.getUmbrales().stream()
                        .filter(u -> u.getNivel() != NivelAlerta.NORMAL)
                        .map(u -> describir(m.getMetrica().getCodigo(), m.getInstancia(), u)))
                .distinct()
                .collect(Collectors.joining(", "));
        return fuera.isEmpty() ? "Métricas dentro de los umbrales" : "Persisten umbrales activados: " + fuera;
    }

    private static String describir(String codigo, String instancia, EvaluacionUmbral u) {
        return (instancia == null || instancia.isEmpty() ? codigo : codigo + " (" + instancia + ")")
                + " en " + u.getNivel().name().toLowerCase();
    }

    private enum Accion { NADA, ANALIZAR, CONCLUIR }

    private record Paso(Accion accion, List<Integer> evaluaciones, Map<Integer, String> problemas) {

        static Paso nada() {
            return new Paso(Accion.NADA, List.of(), Map.of());
        }
    }
}
