package com.komainos.mantenimiento.service;

import com.komainos.alerta.model.TipoAlerta;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.catalogo.model.Tarea;
import com.komainos.catalogo.model.VersionScript;
import com.komainos.catalogo.service.ServicioCatalogoAutomatizacion;
import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.ConfiguracionGrupoRepositorio;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.repository.GrupoMantenimientoRepositorio;
import com.komainos.inventario.service.ServicioParametrosSistema;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.Mop;
import com.komainos.mantenimiento.model.MopTarea;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.mantenimiento.repository.MopRepositorio;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.Rol;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.repository.UsuarioRepositorio;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.util.Tiempo;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Evaluación previa de una orden (RF38-RF42, EA02) en tres fases. Preparar: comprueba la política del entorno y la
 * cuenta, pasa la orden a EN_EVALUACION y deja un análisis en curso por servidor. Analizar: ejecuta los scripts sin
 * transacción. Concluir: genera una MOP por servidor con las tareas activadas y su versión fijada, elige el piloto y
 * aplica la política de cambio. Si falta un análisis o una implementación compatible, la orden queda SIN_IMPLEMENTACION
 * y se alerta al administrador; si ningún servidor necesita intervención, el ciclo se cierra.
 */
@Service
@Slf4j
public class ServicioEvaluacionPrevia {

    private final OrdenRepositorio ordenes;
    private final MopRepositorio mops;
    private final EvaluacionRepositorio evaluacionesRepositorio;
    private final UsuarioRepositorio usuarios;
    private final ServicioConsultaOrdenes consulta;
    private final ServicioEvaluaciones evaluaciones;
    private final EjecutorAnalisis analisis;
    private final ServicioCatalogoAutomatizacion catalogo;
    private final ServicioPoliticasCambio politicas;
    private final ServicioAutorizaciones autorizaciones;
    private final ServicioCierreMantenimiento cierre;
    private final ServicioNotificaciones notificaciones;
    private final ServicioParametrosSistema parametros;
    private final ServicioServidor servidores;
    private final GrupoMantenimientoRepositorio grupos;
    private final ConfiguracionServidorRepositorio configuracionesServidor;
    private final ConfiguracionGrupoRepositorio configuracionesGrupo;
    private final BloqueoAutomatizacion bloqueo;
    private final ServicioAuditoria auditoria;
    private final Clock reloj;
    private final TransactionTemplate transaccion;
    private final ExecutorService segundoPlano = Executors.newVirtualThreadPerTaskExecutor();

    public ServicioEvaluacionPrevia(OrdenRepositorio ordenes, MopRepositorio mops,
                                    EvaluacionRepositorio evaluacionesRepositorio, UsuarioRepositorio usuarios,
                                    ServicioConsultaOrdenes consulta, ServicioEvaluaciones evaluaciones,
                                    EjecutorAnalisis analisis, ServicioCatalogoAutomatizacion catalogo,
                                    ServicioPoliticasCambio politicas, ServicioAutorizaciones autorizaciones,
                                    ServicioCierreMantenimiento cierre, ServicioNotificaciones notificaciones,
                                    ServicioParametrosSistema parametros, ServicioServidor servidores,
                                    GrupoMantenimientoRepositorio grupos,
                                    ConfiguracionServidorRepositorio configuracionesServidor,
                                    ConfiguracionGrupoRepositorio configuracionesGrupo, BloqueoAutomatizacion bloqueo,
                                    ServicioAuditoria auditoria, Clock reloj, PlatformTransactionManager transacciones) {
        this.ordenes = ordenes;
        this.mops = mops;
        this.evaluacionesRepositorio = evaluacionesRepositorio;
        this.usuarios = usuarios;
        this.consulta = consulta;
        this.evaluaciones = evaluaciones;
        this.analisis = analisis;
        this.catalogo = catalogo;
        this.politicas = politicas;
        this.autorizaciones = autorizaciones;
        this.cierre = cierre;
        this.notificaciones = notificaciones;
        this.parametros = parametros;
        this.servidores = servidores;
        this.grupos = grupos;
        this.configuracionesServidor = configuracionesServidor;
        this.configuracionesGrupo = configuracionesGrupo;
        this.bloqueo = bloqueo;
        this.auditoria = auditoria;
        this.reloj = reloj;
        this.transaccion = new TransactionTemplate(transacciones);
    }

    /** Las tres fases seguidas: procesos programados, condición crítica y pruebas */
    public void evaluar(Integer idOrden, Actor actor) {
        Preparacion preparacion = preparar(idOrden, actor);
        analisis.ejecutarTodas(preparacion.evaluaciones());
        concluir(idOrden, preparacion.problemas(), actor);
    }

    /**
     * Desde el panel: los errores de configuración se informan al instante y los análisis siguen en segundo plano; la
     * página de la orden muestra el avance.
     */
    public void iniciar(Integer idOrden, Actor actor) {
        Preparacion preparacion = preparar(idOrden, actor);
        segundoPlano.submit(() -> {
            try {
                analisis.ejecutarTodas(preparacion.evaluaciones());
                concluir(idOrden, preparacion.problemas(), actor);
            } catch (RuntimeException ex) {
                log.error("No se pudo completar la evaluación de la orden {}", idOrden, ex);
            }
        });
    }

    /**
     * Antes de crear un lanzamiento inmediato (RF31): si la orden no podría evaluarse, se informa sin crearla. Comprueba
     * la política del entorno, la cuenta y el script de análisis de cada servidor.
     */
    public void validarLanzamiento(Integer idServidor, Integer idGrupo) {
        transaccion.executeWithoutResult(estado -> {
            List<Servidor> integrantes = idServidor != null
                    ? List.of(servidores.obtenerSinAlcance(idServidor))
                    : grupos.findConIntegrantesById(idGrupo)
                    .orElseThrow(() -> RecursoNoEncontradoException.de("el grupo", idGrupo)).servidores();
            if (integrantes.isEmpty()) {
                throw new ReglaNegocioException("El grupo no tiene integrantes");
            }
            politicas.exigirCompleta(integrantes.getFirst().getEntorno());
            Integer propia = idServidor != null
                    ? configuracionesServidor.findByServidorId(idServidor).map(c -> c.getIdCuentaServicio()).orElse(null)
                    : configuracionesGrupo.findByGrupoId(idGrupo).map(c -> c.getIdCuentaServicio()).orElse(null);
            if (evaluaciones.cuentaAplicable(propia) == null) {
                throw new ReglaNegocioException("Asigne una cuenta de servicio o defina la predeterminada antes de lanzar");
            }
            for (Servidor s : integrantes) {
                if (catalogo.buscarAnalisis(s.getVersionSistemaOperativo().getId()).isEmpty()) {
                    throw new ReglaNegocioException("No hay un script de análisis habilitado para %s (servidor %s)"
                            .formatted(s.getVersionSistemaOperativo().descripcionCompleta(), s.getHostname()));
                }
            }
        });
    }

    /** RF42: con la ejecución bloqueada, el administrador cierra el ciclo registrando el motivo */
    public void cerrarSinImplementacion(Integer idOrden, String motivo, Actor actor) {
        transaccion.executeWithoutResult(estado -> {
            bloqueo.tomar();
            Orden orden = consulta.obtener(idOrden, new AlcanceUsuario(actor.usuarioId(), Rol.ADMINISTRADOR));
            if (orden.getEstado() != EstadoOrden.SIN_IMPLEMENTACION) {
                throw new ReglaNegocioException("Solo se cierran sin intervención las órdenes sin implementación");
            }
            cierre.cerrar(orden, "Cerrada sin intervención: " + motivo, actor);
        });
    }

    /** Órdenes programadas cuya fecha de evaluación previa ya llegó (RF38) */
    public List<Integer> pendientes(int limite) {
        Instant ahora = Tiempo.ahora(reloj);
        return transaccion.execute(estado -> ordenes
                .findByEstadoInOrderByFechaInicioProgramadaAsc(EnumSet.of(EstadoOrden.PROGRAMADA)).stream()
                .filter(o -> !o.getFechaEvaluacionProgramada().isAfter(ahora))
                .limit(limite)
                .map(Orden::getId)
                .toList());
    }

    Preparacion preparar(Integer idOrden, Actor actor) {
        return transaccion.execute(estado -> {
            bloqueo.tomar();
            Orden orden = consulta.obtener(idOrden, new AlcanceUsuario(actor.usuarioId(), Rol.ADMINISTRADOR));
            if (orden.getEstado() != EstadoOrden.PROGRAMADA) {
                throw new ReglaNegocioException("Solo se evalúan órdenes programadas; %s está en %s"
                        .formatted(orden.getCodigo(), orden.getEstado()));
            }
            politicas.exigirCompleta(orden.getDetalles().getFirst().getServidor().getEntorno());
            Integer cuenta = evaluaciones.cuentaAplicable(orden.getIdCuentaServicio());
            if (cuenta == null) {
                throw new ReglaNegocioException("La orden %s no tiene cuenta de servicio y no hay una predeterminada"
                        .formatted(orden.getCodigo()));
            }
            Instant ahora = Tiempo.ahora(reloj);
            orden.cambiarEstado(EstadoOrden.EN_EVALUACION, usuario(actor), "Evaluación previa del mantenimiento", ahora);
            orden.fijarCuentaServicio(cuenta);

            List<Integer> iniciadas = new ArrayList<>();
            Map<Integer, String> problemas = new LinkedHashMap<>();
            for (OrdenDetalle detalle : orden.getDetalles()) {
                Servidor servidor = detalle.getServidor();
                if (detalle.getEstado() == EstadoDetalleOrden.NO_INICIADO) {
                    continue;
                }
                if (servidor.getEstado() != EstadoServidor.ACTIVO) {
                    detalle.omitirSiPendiente();
                    continue;
                }
                try {
                    iniciadas.add(evaluaciones.iniciar(servidor, detalle, TipoEvaluacion.PREVIA, cuenta).getId());
                } catch (ReglaNegocioException ex) {
                    problemas.put(detalle.getId(), "%s: %s".formatted(servidor.getHostname(), ex.getMessage()));
                }
            }
            auditoria.registrar(actor, Operacion.de("INICIAR_EVALUACION_PREVIA", "orden", idOrden).sobreOrden(idOrden)
                    .motivo("%d servidor(es) en análisis".formatted(iniciadas.size())));
            return new Preparacion(iniciadas, problemas);
        });
    }

    /** Idempotente: si la orden ya salió de EN_EVALUACION o la MOP ya existe, no repite nada */
    void concluir(Integer idOrden, Map<Integer, String> problemasPrevios, Actor actor) {
        try {
            transaccion.executeWithoutResult(estado -> generarMopYAutorizar(idOrden, problemasPrevios, actor));
        } catch (ReglaNegocioException ex) {
            // Por ejemplo, una regla de la política desactivada entre la preparación y la conclusión
            transaccion.executeWithoutResult(estado -> bloquear(idOrden, List.of(ex.getMessage()), actor));
        }
    }

    private void generarMopYAutorizar(Integer idOrden, Map<Integer, String> problemasPrevios, Actor actor) {
        bloqueo.tomar();
        Orden orden = ordenes.findConDetalleById(idOrden).orElseThrow();
        if (orden.getEstado() != EstadoOrden.EN_EVALUACION) {
            return;
        }
        Instant ahora = Tiempo.ahora(reloj);
        int maximoMop = parametros.obtener().getMaxDuracionMopMinutos();
        List<String> problemas = new ArrayList<>();
        List<Mop> generadas = new ArrayList<>();

        for (OrdenDetalle detalle : orden.getDetalles()) {
            if (detalle.getEstado() == EstadoDetalleOrden.NO_INICIADO || mops.existsByDetalleId(detalle.getId())) {
                mops.findByDetalleId(detalle.getId()).ifPresent(generadas::add);
                continue;
            }
            Optional<Evaluacion> previa = evaluacionesRepositorio.findByDetalleIdOrderByFechaInicioAsc(detalle.getId())
                    .stream().filter(e -> e.getTipo() == TipoEvaluacion.PREVIA).reduce((a, b) -> b);
            String servidor = detalle.getServidor().getHostname();
            if (previa.isEmpty()) {
                // Sin análisis no se sabe si el servidor necesita intervención: nunca se asume que no
                problemas.add(problemasPrevios.getOrDefault(detalle.getId(),
                        "%s: no se pudo iniciar el análisis previo".formatted(servidor)));
                continue;
            }
            if (!previa.get().estaFinalizada()) {
                problemas.add("%s: el análisis previo no fue concluyente (%s)".formatted(servidor, previa.get().getError()));
                continue;
            }
            // RF41: cada tarea una sola vez y por prioridad; RF42: la versión vigente compatible queda fijada
            List<Tarea> tareas = catalogo.activadas(previa.get().umbralesActivados());
            if (tareas.isEmpty()) {
                detalle.omitirSiPendiente();
                continue;
            }
            Integer idVersionSo = detalle.getServidor().getVersionSistemaOperativo().getId();
            List<VersionScript> versiones = tareas.stream()
                    .map(t -> catalogo.compatible(t.getScript().getId(), idVersionSo).orElse(null))
                    .toList();
            Mop mop = mops.save(Mop.generar(detalle, tareas, versiones, orden.getIdCuentaServicio(), ahora));
            generadas.add(mop);
            mop.getTareas().stream().filter(t -> t.getVersion() == null).map(MopTarea::getTarea).forEach(t ->
                    problemas.add("%s: la tarea «%s» no tiene una implementación habilitada para %s".formatted(servidor,
                            t.getNombre(), detalle.getServidor().getVersionSistemaOperativo().descripcionCompleta())));
            if (mop.duracionTotal() > maximoMop) {
                auditoria.registrar(actor, Operacion.de("ADVERTENCIA_DURACION_MOP", "mop", mop.getId())
                        .sobreOrden(idOrden).sobreServidor(detalle.getServidor().getId())
                        .motivo("La MOP de %s estima %d min y supera el máximo de %d min por MOP (RF41)"
                                .formatted(servidor, mop.duracionTotal(), maximoMop)));
            }
        }

        if (!problemas.isEmpty()) {
            bloquear(idOrden, problemas, actor);
            return;
        }
        if (generadas.isEmpty()) {
            cierre.cerrar(orden, "La evaluación previa no requiere intervención: las métricas están dentro de los umbrales",
                    actor);
            notificaciones.aOrden(orden, TipoAlerta.CIERRE_MANTENIMIENTO, orden.getCodigo() + " sin intervención",
                    "La evaluación previa no activó tareas de mantenimiento; el ciclo se cerró.",
                    notificaciones.involucrados(orden));
            return;
        }
        // RF58: en un grupo paralelo, el servidor con más tareas es el piloto; ante empate, el primero del grupo
        if (orden.esGrupal() && orden.getModoEjecucionAplicado() == ModoEjecucion.PARALELO) {
            generadas.stream()
                    .max(Comparator.comparingInt((Mop m) -> m.getTareas().size())
                            .thenComparing(m -> -m.getDetalle().getPosicionEjecucion()))
                    .ifPresent(m -> m.getDetalle().marcarPiloto());
        }
        autorizaciones.solicitar(orden, actor);
    }

    /** RF42: la orden no puede ejecutarse; el administrador revisa y cierra o corrige y relanza */
    private void bloquear(Integer idOrden, List<String> problemas, Actor actor) {
        bloqueo.tomar();
        Orden orden = ordenes.findConDetalleById(idOrden).orElseThrow();
        if (orden.getEstado() != EstadoOrden.EN_EVALUACION) {
            return;
        }
        String detalle = String.join("; ", problemas);
        orden.cambiarEstado(EstadoOrden.SIN_IMPLEMENTACION, null, recortar("Ejecución bloqueada: " + detalle, 1000),
                Tiempo.ahora(reloj));
        notificaciones.aOrden(orden, TipoAlerta.SIN_IMPLEMENTACION, "Mantenimiento bloqueado: " + orden.getCodigo(),
                recortar(detalle, 2000), notificaciones.administradores());
        auditoria.registrar(actor, Operacion.de("BLOQUEAR_ORDEN", "orden", idOrden).sobreOrden(idOrden)
                .motivo(recortar(detalle, 1000)));
    }

    private Usuario usuario(Actor actor) {
        return actor.esSistema() ? null : usuarios.findById(actor.usuarioId()).orElse(null);
    }

    private static String recortar(String texto, int largo) {
        return texto.length() <= largo ? texto : texto.substring(0, largo - 1) + "…";
    }

    @PreDestroy
    void detener() {
        segundoPlano.shutdown();
    }

    /**
     * @param evaluaciones análisis en curso, uno por servidor
     * @param problemas    por detalle, por qué el servidor no pudo analizarse (cuenta o script de análisis)
     */
    record Preparacion(List<Integer> evaluaciones, Map<Integer, String> problemas) {
    }
}
