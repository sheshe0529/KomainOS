package com.komainos.mantenimiento.service;

import com.komainos.alerta.model.TipoAlerta;
import com.komainos.inventario.model.ConfiguracionGrupo;
import com.komainos.inventario.model.ConfiguracionServidor;
import com.komainos.inventario.model.EstadoGrupo;
import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.GrupoMantenimiento;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.ConfiguracionGrupoRepositorio;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.OrigenOrden;
import com.komainos.mantenimiento.model.RevisionPeriodicaGrupo;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.mantenimiento.repository.RevisionPeriodicaGrupoRepositorio;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Revisión periódica (RF37, RF40, EA02) con calendarios independientes para servidores y grupos. La advertencia solo
 * alerta (CP102); la condición crítica inicia un ciclo del mismo objetivo sujeto a la política de cambio (CP107).
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
    private final ConfiguracionGrupoRepositorio configuracionesGrupo;
    private final OrdenRepositorio ordenes;
    private final EvaluacionRepositorio evaluacionesRepositorio;
    private final RevisionPeriodicaGrupoRepositorio revisionesGrupo;
    private final ServicioEvaluaciones evaluaciones;
    private final EjecutorAnalisis analisis;
    private final ServicioNotificaciones notificaciones;
    private final PuertoProgramacionCambio programacion;
    private final ServicioEvaluacionPrevia evaluacionPrevia;
    private final BloqueoAutomatizacion bloqueo;
    private final Clock reloj;
    private final TransactionTemplate transaccion;

    public ServicioRevisionPeriodica(ServicioServidor servidores, ConfiguracionServidorRepositorio configuraciones,
                                     ConfiguracionGrupoRepositorio configuracionesGrupo, OrdenRepositorio ordenes,
                                     EvaluacionRepositorio evaluacionesRepositorio, RevisionPeriodicaGrupoRepositorio revisionesGrupo,
                                     ServicioEvaluaciones evaluaciones, EjecutorAnalisis analisis,
                                     ServicioNotificaciones notificaciones, PuertoProgramacionCambio programacion,
                                     ServicioEvaluacionPrevia evaluacionPrevia, BloqueoAutomatizacion bloqueo,
                                     Clock reloj, PlatformTransactionManager transacciones) {
        this.servidores = servidores;
        this.configuraciones = configuraciones;
        this.configuracionesGrupo = configuracionesGrupo;
        this.ordenes = ordenes;
        this.evaluacionesRepositorio = evaluacionesRepositorio;
        this.revisionesGrupo = revisionesGrupo;
        this.evaluaciones = evaluaciones;
        this.analisis = analisis;
        this.notificaciones = notificaciones;
        this.programacion = programacion;
        this.evaluacionPrevia = evaluacionPrevia;
        this.bloqueo = bloqueo;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** Revisión individual solicitada desde la ficha del servidor. */
    public Evaluacion revisar(Integer idServidor, Actor actor) {
        Integer idEvaluacion = iniciarIndividual(idServidor, false);
        revisarIndividual(idServidor, idEvaluacion, actor);
        return evaluaciones.obtener(idEvaluacion);
    }

    public boolean revisarPendiente(Integer idServidor, Actor actor) {
        Integer idEvaluacion = iniciarIndividual(idServidor, true);
        if (idEvaluacion == null) {
            return false;
        }
        revisarIndividual(idServidor, idEvaluacion, actor);
        return true;
    }

    private Integer iniciarIndividual(Integer idServidor, boolean soloPendiente) {
        return transaccion.execute(estado -> {
            Servidor servidor = servidores.obtenerSinAlcance(idServidor);
            if (servidor.getEstado() != EstadoServidor.ACTIVO) {
                throw new ReglaNegocioException("Solo se revisan servidores activos; %s está %s"
                        .formatted(servidor.getHostname(), servidor.getEstado()));
            }
            ConfiguracionServidor config = (soloPendiente ? configuraciones.findParaRevision(idServidor)
                    : configuraciones.findByServidorId(idServidor))
                    .orElseThrow(() -> new ReglaNegocioException("El servidor %s no tiene configuración de mantenimiento"
                            .formatted(servidor.getHostname())));
            if (soloPendiente && !individualPendiente(config, Tiempo.ahora(reloj))) {
                return null;
            }
            return evaluaciones.iniciar(servidor, null, TipoEvaluacion.REVISION_PERIODICA, config.getIdCuentaServicioEvaluacion())
                    .getId();
        });
    }

    private void revisarIndividual(Integer idServidor, Integer idEvaluacion, Actor actor) {
        analisis.ejecutar(idEvaluacion);
        if (Boolean.TRUE.equals(transaccion.execute(estado -> avisar(idEvaluacion)))) {
            atenderCondicionCritica(idServidor, null, idEvaluacion, actor);
        }
    }

    public boolean revisarGrupoPendiente(Integer idGrupo, Actor actor) {
        List<Integer> idsEvaluacion = transaccion.execute(estado -> iniciarGrupo(idGrupo));
        if (idsEvaluacion == null) {
            return false;
        }
        analisis.ejecutarTodas(idsEvaluacion);
        Integer critica = transaccion.execute(estado -> {
            Integer primeraCritica = null;
            for (Integer idEvaluacion : idsEvaluacion) {
                if (avisar(idEvaluacion) && primeraCritica == null) {
                    primeraCritica = idEvaluacion;
                }
            }
            return primeraCritica;
        });
        if (critica != null) {
            atenderCondicionCritica(null, idGrupo, critica, actor);
        }
        return true;
    }

    private List<Integer> iniciarGrupo(Integer idGrupo) {
        ConfiguracionGrupo config = configuracionesGrupo.findParaRevision(idGrupo).orElse(null);
        Instant ahora = Tiempo.ahora(reloj);
        if (config == null || !grupoPendiente(config, ahora)) {
            return null;
        }
        GrupoMantenimiento grupo = config.getGrupo();
        List<Servidor> integrantes = grupo.servidores().stream().filter(s -> !s.estaDadoDeBaja()).toList();
        if (integrantes.isEmpty()) {
            return null;
        }
        RevisionPeriodicaGrupo revision = revisionesGrupo.save(RevisionPeriodicaGrupo.iniciar(grupo, ahora));
        List<Integer> idsEvaluacion = new ArrayList<>();
        for (Servidor integrante : integrantes) {
            try {
                idsEvaluacion.add(evaluaciones.iniciarRevisionGrupal(integrante, revision,
                        config.getIdCuentaServicioEvaluacion()).getId());
            } catch (ReglaNegocioException ex) {
                notificaciones.aGrupo(grupo, TipoAlerta.ADVERTENCIA_UMBRAL,
                        "Revisión grupal no concluyente: " + integrante.getHostname(), ex.getMessage());
            }
        }
        return idsEvaluacion;
    }

    /**
     * RF40: orden de prioridad alta y su evaluación previa, que aplica la política antes de intervenir. Cada paso en su
     * transacción: si no se puede programar, las alertas de la revisión ya quedaron registradas.
     */
    private void atenderCondicionCritica(Integer idServidor, Integer idGrupo, Integer idEvaluacion, Actor actor) {
        Integer idOrden;
        try {
            idOrden = transaccion.execute(estado -> {
                bloqueo.tomar();
                return tieneCicloEnMarcha(idServidor, idGrupo) ? null : programacion.inmediata(idServidor, idGrupo, false,
                        "Condición crítica detectada en la revisión periódica", idEvaluacion, actor).getId();
            });
        } catch (ReglaNegocioException ex) {
            log.warn("No se pudo programar la orden por condición crítica de la evaluación {}: {}", idEvaluacion, ex.getMessage());
            transaccion.executeWithoutResult(estado -> notificar(evaluaciones.obtener(idEvaluacion),
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
                .filter(c -> individualPendiente(c, ahora))
                .limit(limite)
                .map(c -> c.getServidor().getId())
                .toList());
    }

    public List<Integer> gruposPendientes(int limite) {
        Instant ahora = Tiempo.ahora(reloj);
        return transaccion.execute(estado -> configuracionesGrupo
                .findByRevisionPeriodicaHabilitadaTrueAndGrupoEstadoOrderByGrupoNombreAsc(EstadoGrupo.ACTIVO).stream()
                .filter(c -> grupoPendiente(c, ahora))
                .filter(c -> c.getGrupo().servidores().stream().anyMatch(s -> !s.estaDadoDeBaja()))
                .limit(limite)
                .map(c -> c.getGrupo().getId())
                .toList());
    }

    private boolean individualPendiente(ConfiguracionServidor config, Instant ahora) {
        return config.isRevisionPeriodicaHabilitada() && evaluacionesRepositorio
                .findFirstByServidorIdAndTipoAndRevisionGrupoIsNullOrderByFechaInicioDesc(config.getServidor().getId(),
                        TipoEvaluacion.REVISION_PERIODICA)
                .map(e -> vencida(e.getFechaInicio(), config.getFrecuenciaRevisionDias(), ahora))
                .orElse(true);
    }

    private boolean grupoPendiente(ConfiguracionGrupo config, Instant ahora) {
        return config.isRevisionPeriodicaHabilitada() && config.getGrupo().getEstado() == EstadoGrupo.ACTIVO
                && revisionesGrupo.findFirstByGrupoIdOrderByFechaInicioDesc(config.getGrupo().getId())
                .map(r -> vencida(r.getFechaInicio(), config.getFrecuenciaRevisionDias(), ahora))
                .orElse(true);
    }

    private static boolean vencida(Instant inicio, int frecuenciaDias, Instant ahora) {
        return !inicio.plus(Duration.ofDays(frecuenciaDias)).isAfter(ahora);
    }

    /** RF37: alerta al responsable y a los administradores; devuelve si se alcanzó la condición crítica */
    private boolean avisar(Integer idEvaluacion) {
        bloqueo.tomar();
        Evaluacion e = evaluaciones.obtener(idEvaluacion);
        Servidor servidor = e.getServidor();
        GrupoMantenimiento grupo = e.getRevisionGrupo() == null ? null : e.getRevisionGrupo().getGrupo();
        String objetivo = grupo == null ? servidor.getHostname() : servidor.getHostname() + " en " + grupo.getNombre();
        if (!e.estaFinalizada()) {
            // CP103: la revisión no concluyente se registra y se alerta
            notificar(e, TipoAlerta.ADVERTENCIA_UMBRAL, "Revisión no concluyente: " + objetivo,
                    e.getError() != null ? e.getError() : "La revisión no registró un resultado");
            return false;
        }
        NivelAlerta nivel = e.nivel();
        if (nivel == NivelAlerta.ADVERTENCIA) {
            notificar(e, TipoAlerta.ADVERTENCIA_UMBRAL, "Advertencia en " + objetivo,
                    "La revisión periódica encontró métricas en advertencia: " + umbrales(e));
        }
        if (nivel != NivelAlerta.CRITICO) {
            return false;
        }
        String atencion = tieneCicloEnMarcha(grupo == null ? servidor.getId() : null, grupo == null ? null : grupo.getId())
                ? ". El objetivo ya tiene un mantenimiento en marcha: la condición se atiende en esa orden."
                : ". Se programa un mantenimiento de prioridad alta, sujeto a la política de cambio.";
        notificar(e, TipoAlerta.CONDICION_CRITICA, "Condición crítica en " + objetivo,
                "La revisión periódica encontró métricas en nivel crítico: " + umbrales(e) + atencion);
        return true;
    }

    private void notificar(Evaluacion e, TipoAlerta tipo, String titulo, String mensaje) {
        if (e.getRevisionGrupo() == null) {
            notificaciones.aServidor(e.getServidor(), tipo, titulo, mensaje);
        } else {
            notificaciones.aGrupo(e.getRevisionGrupo().getGrupo(), tipo, titulo, mensaje);
        }
    }

    /** Una orden normal ya programada no impide atender la condición crítica; una en curso o crítica pendiente sí */
    private boolean tieneCicloEnMarcha(Integer idServidor, Integer idGrupo) {
        Set<EstadoOrden> estados = EnumSet.copyOf(EN_MARCHA);
        estados.add(EstadoOrden.PROGRAMADA);
        var candidatas = idGrupo == null ? ordenes.findByServidorIdAndEstadoIn(idServidor, estados)
                : ordenes.findByGrupoIdAndEstadoIn(idGrupo, estados);
        return candidatas.stream()
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
