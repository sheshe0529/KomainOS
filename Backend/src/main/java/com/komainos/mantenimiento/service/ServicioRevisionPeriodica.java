package com.komainos.mantenimiento.service;

import com.komainos.alerta.model.TipoAlerta;
import com.komainos.inventario.model.ConfiguracionServidor;
import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.OrigenOrden;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Revisión periódica (RF37, RF40, EA02): ejecuta el análisis según la frecuencia de revisión del servidor, registra sus
 * métricas y avisa al responsable y a los administradores. La advertencia solo alerta (CP102); la condición crítica
 * genera una orden de prioridad alta que pasa por la evaluación y la política de cambio antes de intervenir (CP107).
 */
@Service
@Slf4j
public class ServicioRevisionPeriodica {

    /** Con alguna de estas el servidor ya tiene un ciclo en marcha: la condición crítica no crea otra orden */
    private static final Set<EstadoOrden> EN_MARCHA = EnumSet.of(EstadoOrden.EN_EVALUACION, EstadoOrden.SIN_IMPLEMENTACION,
            EstadoOrden.PENDIENTE_AUTORIZACION, EstadoOrden.AUTORIZADA, EstadoOrden.EN_COLA, EstadoOrden.EN_EJECUCION,
            EstadoOrden.PENDIENTE_VALIDACION, EstadoOrden.INCIDENCIA);

    private final ServicioServidor servidores;
    private final ConfiguracionServidorRepositorio configuraciones;
    private final OrdenRepositorio ordenes;
    private final EvaluacionRepositorio evaluacionesRepositorio;
    private final ServicioEvaluaciones evaluaciones;
    private final EjecutorAnalisis analisis;
    private final ServicioNotificaciones notificaciones;
    private final PuertoProgramacionCambio programacion;
    private final ServicioEvaluacionPrevia evaluacionPrevia;
    private final BloqueoAutomatizacion bloqueo;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public ServicioRevisionPeriodica(ServicioServidor servidores, ConfiguracionServidorRepositorio configuraciones,
                                     OrdenRepositorio ordenes, EvaluacionRepositorio evaluacionesRepositorio,
                                     ServicioEvaluaciones evaluaciones, EjecutorAnalisis analisis,
                                     ServicioNotificaciones notificaciones, PuertoProgramacionCambio programacion,
                                     ServicioEvaluacionPrevia evaluacionPrevia, BloqueoAutomatizacion bloqueo,
                                     Clock reloj, PlatformTransactionManager transacciones) {
        this.servidores = servidores;
        this.configuraciones = configuraciones;
        this.ordenes = ordenes;
        this.evaluacionesRepositorio = evaluacionesRepositorio;
        this.evaluaciones = evaluaciones;
        this.analisis = analisis;
        this.notificaciones = notificaciones;
        this.programacion = programacion;
        this.evaluacionPrevia = evaluacionPrevia;
        this.bloqueo = bloqueo;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** Las tres fases del análisis y, si es crítico, la orden y su evaluación previa */
    public Evaluacion revisar(Integer idServidor, Actor actor) {
        Integer idEvaluacion = transaccion.execute(estado -> {
            Servidor servidor = servidores.obtenerSinAlcance(idServidor);
            if (servidor.getEstado() != EstadoServidor.ACTIVO) {
                throw new ReglaNegocioException("Solo se revisan servidores activos; %s está %s"
                        .formatted(servidor.getHostname(), servidor.getEstado()));
            }
            ConfiguracionServidor config = configuraciones.findByServidorId(idServidor)
                    .orElseThrow(() -> new ReglaNegocioException("El servidor %s no tiene configuración de mantenimiento"
                            .formatted(servidor.getHostname())));
            return evaluaciones.iniciar(servidor, null, TipoEvaluacion.REVISION_PERIODICA, config.getIdCuentaServicio())
                    .getId();
        });
        analisis.ejecutar(idEvaluacion);
        Boolean critica = transaccion.execute(estado -> avisar(idEvaluacion));
        if (Boolean.TRUE.equals(critica)) {
            atenderCondicionCritica(idServidor, idEvaluacion, actor);
        }
        return evaluaciones.obtener(idEvaluacion);
    }

    /**
     * RF40: orden de prioridad alta y su evaluación previa, que aplica la política antes de intervenir. Cada paso en su
     * transacción: si no se puede programar, las alertas de la revisión ya quedaron registradas.
     */
    private void atenderCondicionCritica(Integer idServidor, Integer idEvaluacion, Actor actor) {
        Integer idOrden;
        try {
            idOrden = transaccion.execute(estado -> {
                bloqueo.tomar();
                return tieneCicloEnMarcha(idServidor) ? null : programacion.inmediata(idServidor, null, false,
                        "Condición crítica detectada en la revisión periódica", idEvaluacion, actor).getId();
            });
        } catch (ReglaNegocioException ex) {
            log.warn("No se pudo programar la orden por condición crítica del servidor {}: {}", idServidor, ex.getMessage());
            transaccion.executeWithoutResult(estado -> notificaciones.aServidor(servidores.obtenerSinAlcance(idServidor),
                    TipoAlerta.CONDICION_CRITICA, "Condición crítica sin programar",
                    "No se pudo programar el mantenimiento: " + ex.getMessage()));
            return;
        }
        if (idOrden == null) {
            return;
        }
        try {
            evaluacionPrevia.evaluar(idOrden, actor);
        } catch (ReglaNegocioException ex) {
            log.warn("La orden por condición crítica {} quedó programada sin evaluar: {}", idOrden, ex.getMessage());
        }
    }

    /** Servidores activos cuya última revisión es más antigua que su frecuencia de revisión (RF37) */
    public List<Integer> pendientes(int limite) {
        Instant ahora = Tiempo.ahora(reloj);
        return transaccion.execute(estado -> configuraciones.findAllByOrderByServidorHostnameAsc().stream()
                .filter(c -> c.getServidor().getEstado() == EstadoServidor.ACTIVO)
                .filter(c -> evaluacionesRepositorio
                        .findFirstByServidorIdAndTipoOrderByFechaInicioDesc(c.getServidor().getId(),
                                TipoEvaluacion.REVISION_PERIODICA)
                        .map(e -> !e.getFechaInicio().plus(Duration.ofDays(c.getFrecuenciaRevisionDias())).isAfter(ahora))
                        .orElse(true))
                .limit(limite)
                .map(c -> c.getServidor().getId())
                .toList());
    }

    /** RF37: alerta al responsable y a los administradores; devuelve si se alcanzó la condición crítica */
    private boolean avisar(Integer idEvaluacion) {
        bloqueo.tomar();
        Evaluacion e = evaluaciones.obtener(idEvaluacion);
        Servidor servidor = e.getServidor();
        if (!e.estaFinalizada()) {
            // CP103: la revisión no concluyente se registra y se alerta
            notificaciones.aServidor(servidor, TipoAlerta.ADVERTENCIA_UMBRAL,
                    "Revisión no concluyente: " + servidor.getHostname(), e.getError());
            return false;
        }
        NivelAlerta nivel = e.nivel();
        if (nivel == NivelAlerta.ADVERTENCIA) {
            notificaciones.aServidor(servidor, TipoAlerta.ADVERTENCIA_UMBRAL, "Advertencia en " + servidor.getHostname(),
                    "La revisión periódica encontró métricas en advertencia: " + umbrales(e));
        }
        if (nivel != NivelAlerta.CRITICO) {
            return false;
        }
        // El texto dice lo que realmente ocurrirá: una orden nueva o la que ya está en marcha
        String atencion = tieneCicloEnMarcha(servidor.getId())
                ? ". El servidor ya tiene un mantenimiento en marcha: la condición se atiende en esa orden."
                : ". Se programa un mantenimiento de prioridad alta, sujeto a la política de cambio.";
        notificaciones.aServidor(servidor, TipoAlerta.CONDICION_CRITICA, "Condición crítica en " + servidor.getHostname(),
                "La revisión periódica encontró métricas en nivel crítico: " + umbrales(e) + atencion);
        return true;
    }

    /** Una orden normal ya programada no impide atender la condición crítica; una en curso o crítica pendiente sí */
    private boolean tieneCicloEnMarcha(Integer idServidor) {
        return ordenes.findDelServidorEnEstados(idServidor, EnumSet.allOf(EstadoOrden.class)).stream()
                .anyMatch(o -> EN_MARCHA.contains(o.getEstado())
                        || (o.getEstado() == EstadoOrden.PROGRAMADA && o.getOrigen() == OrigenOrden.CONDICION_CRITICA));
    }

    private static String umbrales(Evaluacion e) {
        return e.getMediciones().stream()
                .filter(m -> m.getUmbrales().stream().anyMatch(u -> u.getNivel() != NivelAlerta.NORMAL))
                .map(m -> m.getInstancia().isEmpty() ? m.getMetrica().getNombre()
                        : m.getMetrica().getNombre() + " (" + m.getInstancia() + ")")
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("—");
    }
}
