package com.komainos.planificacion.service;

import com.komainos.auditoria.service.ServicioAuditoria.Operacion;
import com.komainos.auditoria.service.ServicioAuditoria;
import com.komainos.inventario.event.ConfiguracionMantenimientoActualizada;
import com.komainos.inventario.event.VentanasServidorActualizadas;
import com.komainos.inventario.model.CalendarioSemanal;
import com.komainos.inventario.model.ConfiguracionGrupo;
import com.komainos.inventario.model.ConfiguracionMantenimiento;
import com.komainos.inventario.model.ConfiguracionServidor;
import com.komainos.inventario.model.EstadoGrupo;
import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.GrupoMantenimiento;
import com.komainos.inventario.model.ModalidadPlanificacion;
import com.komainos.inventario.model.ModoEjecucion;
import com.komainos.inventario.model.NivelCriticidad;
import com.komainos.inventario.model.ParametrosSistema;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.repository.ConfiguracionGrupoRepositorio;
import com.komainos.inventario.repository.ConfiguracionServidorRepositorio;
import com.komainos.inventario.repository.GrupoMantenimientoRepositorio;
import com.komainos.inventario.repository.ServidorRepositorio;
import com.komainos.inventario.service.ServicioGrupo;
import com.komainos.inventario.service.ServicioParametrosSistema;
import com.komainos.mantenimiento.model.CierreOrden;
import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.OrigenOrden;
import com.komainos.mantenimiento.model.ProgramacionOrden;
import com.komainos.mantenimiento.repository.CierreOrdenRepositorio;
import com.komainos.mantenimiento.repository.OrdenDetalleRepositorio;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.planificacion.config.PropiedadesPlanificacion;
import com.komainos.planificacion.model.FactorCiclo;
import com.komainos.planificacion.model.ResultadoCiclo;
import com.komainos.planificacion.repository.FactorCicloRepositorio;
import com.komainos.planificacion.service.algoritmo.PlanificadorVoraz;
import com.komainos.planificacion.service.algoritmo.Reserva;
import com.komainos.planificacion.service.algoritmo.ResultadoPlanificacion;
import com.komainos.planificacion.service.algoritmo.SolicitudPlanificacion;
import com.komainos.seguridad.model.Usuario;
import com.komainos.seguridad.repository.UsuarioRepositorio;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import com.komainos.shared.exception.ReglaNegocioException;
import com.komainos.shared.model.Actor;
import com.komainos.shared.model.Intervalo;
import com.komainos.shared.util.Tiempo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Componente de Planificacion (R2.2): genera y gestiona las ordenes del
 * cronograma aplicando el algoritmo voraz (RF27-RF30, RF38, RF46, RF51, RF64).
 *
 * <p>Arma las entradas del algoritmo desde el inventario y las ordenes
 * existentes, lo ejecuta y persiste su resultado en {@code orden},
 * {@code orden_detalle} y {@code programacion_orden}.
 */
@Service
@Slf4j
public class ServicioPlanificacion {

    public static final String PROCESO_AUTOMATICO = "PLANIFICACION_AUTOMATICA";
    private static final Set<EstadoOrden> OCUPAN = EstadoOrden.queOcupanCronograma();

    private final OrdenRepositorio ordenes;
    private final OrdenDetalleRepositorio detalles;
    private final CierreOrdenRepositorio cierres;
    private final FactorCicloRepositorio factores;
    private final ServidorRepositorio servidores;
    private final GrupoMantenimientoRepositorio grupos;
    private final ConfiguracionServidorRepositorio configuracionesServidor;
    private final ConfiguracionGrupoRepositorio configuracionesGrupo;
    private final ServicioParametrosSistema parametros;
    private final UsuarioRepositorio usuarios;
    private final ServicioAuditoria auditoria;
    private final PropiedadesPlanificacion propiedades;
    private final Clock reloj;
    private final TransactionTemplate transaccionNueva;
    private final PlanificadorVoraz planificador = new PlanificadorVoraz();

    public ServicioPlanificacion(OrdenRepositorio ordenes, OrdenDetalleRepositorio detalles,
                                 CierreOrdenRepositorio cierres, FactorCicloRepositorio factores,
                                 ServidorRepositorio servidores, GrupoMantenimientoRepositorio grupos,
                                 ConfiguracionServidorRepositorio configuracionesServidor,
                                 ConfiguracionGrupoRepositorio configuracionesGrupo,
                                 ServicioParametrosSistema parametros, UsuarioRepositorio usuarios,
                                 ServicioAuditoria auditoria, PropiedadesPlanificacion propiedades, Clock reloj,
                                 PlatformTransactionManager transacciones) {
        this.ordenes = ordenes;
        this.detalles = detalles;
        this.cierres = cierres;
        this.factores = factores;
        this.servidores = servidores;
        this.grupos = grupos;
        this.configuracionesServidor = configuracionesServidor;
        this.configuracionesGrupo = configuracionesGrupo;
        this.parametros = parametros;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.propiedades = propiedades;
        this.reloj = reloj;
        this.transaccionNueva = new TransactionTemplate(transacciones);
        this.transaccionNueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // =================================================== programacion manual (RF30)

    /**
     * Programa una orden para un servidor o grupo a solicitud del
     * administrador. Con fecha, la verifica; sin fecha, toma el primer
     * intervalo disponible (RF28).
     */
    @Transactional
    public Orden programar(Integer idServidor, Integer idGrupo, Instant inicio, String motivo, Actor actor) {
        Objetivo objetivo = objetivo(idServidor, idGrupo);
        Instant ahora = Tiempo.ahora(reloj);
        Instant fechaObjetivo = inicio != null ? inicio : ahora;
        SolicitudPlanificacion solicitud = solicitud(objetivo, fechaObjetivo, ahora);
        List<Reserva> reservas = reservas(ahora, fechaObjetivo, null);
        ResultadoPlanificacion resultado = inicio != null
                ? planificador.verificar(solicitud, inicio, reservas)
                : planificador.planificar(solicitud, reservas);

        String texto = textoOPorDefecto(motivo, "Programación solicitada por el administrador");
        Orden orden = crearOrden(objetivo, resultado, OrigenOrden.SOLICITUD_BAJO_DEMANDA, usuarioDe(actor), texto, ahora);
        auditoria.registrar(actor, Operacion.de("PROGRAMAR_ORDEN", "orden", orden.getId())
                .sobreOrden(orden.getId())
                .sobreServidor(idServidor).sobreGrupo(idGrupo)
                .valores(null, instantanea(resultado))
                .motivo(texto));
        return orden;
    }

    /** Propone el primer intervalo disponible sin crear la orden (ayuda del panel al programar). */
    @Transactional(readOnly = true)
    public ResultadoPlanificacion proponer(Integer idServidor, Integer idGrupo, Instant desde) {
        Objetivo objetivo = objetivo(idServidor, idGrupo);
        Instant ahora = Tiempo.ahora(reloj);
        Instant fechaObjetivo = desde != null && desde.isAfter(ahora) ? desde : ahora;
        return planificador.planificar(solicitud(objetivo, fechaObjetivo, ahora), reservas(ahora, fechaObjetivo, null));
    }

    /**
     * RF30: nueva fecha con motivo, conservando la programacion anterior.
     * Sin fecha, el algoritmo busca desde la fecha objetivo original.
     */
    @Transactional
    public Orden reprogramar(Integer idOrden, Instant inicio, String motivo, Actor actor) {
        Orden orden = buscarOrden(idOrden);
        ResultadoPlanificacion resultado = reprogramarInterno(orden, inicio, motivo, usuarioDe(actor));
        auditoria.registrar(actor, Operacion.de("REPROGRAMAR_ORDEN", "orden", idOrden)
                .sobreOrden(idOrden)
                .valores(null, instantanea(resultado))
                .motivo(motivo));
        return orden;
    }

    /** RF30: cancelacion con motivo (DEC-19). */
    @Transactional
    public Orden cancelar(Integer idOrden, String motivo, Actor actor) {
        Orden orden = buscarOrden(idOrden);
        orden.cancelar(usuarioDe(actor), motivo, Tiempo.ahora(reloj));
        auditoria.registrar(actor, Operacion.de("CANCELAR_ORDEN", "orden", idOrden).sobreOrden(idOrden).motivo(motivo));
        return orden;
    }

    // ================================================ ciclos automaticos (RF27, RF29)

    /**
     * Genera la orden de cada servidor y grupo en modalidad automatica que la
     * necesite (EA01). Los objetivos se planifican de mayor a menor
     * criticidad y luego por fecha objetivo, de modo que la criticidad decide
     * quien toma primero un intervalo disputado (RF28). Cada objetivo va en
     * su propia transaccion: un fallo no deshace lo ya planificado.
     */
    public ResumenPlanificacion planificarCiclosAutomaticos() {
        List<Candidato> candidatos = new ArrayList<>(Objects.requireNonNull(
                transaccionNueva.execute(estado -> buscarCandidatos(null, null))));
        candidatos.sort(Comparator.comparingInt(Candidato::prioridad)
                .thenComparing(Candidato::fechaObjetivo)
                .thenComparing(Candidato::descripcion));

        List<String> creadas = new ArrayList<>();
        List<String> omitidas = new ArrayList<>();
        for (Candidato candidato : candidatos) {
            try {
                Orden orden = transaccionNueva.execute(estado -> generarCiclo(candidato));
                if (orden != null) {
                    creadas.add(orden.getCodigo());
                }
            } catch (ReglaNegocioException ex) {
                // Se reintenta en la siguiente corrida (DEC-12); no bloquea al resto.
                log.warn("No se pudo planificar {}: {}", candidato.descripcion(), ex.getMessage());
                omitidas.add(candidato.descripcion() + ": " + ex.getMessage());
            }
        }
        if (!creadas.isEmpty() || !omitidas.isEmpty()) {
            log.info("Planificación automática: {} orden(es) generada(s), {} objetivo(s) sin intervalo",
                    creadas.size(), omitidas.size());
        }
        return new ResumenPlanificacion(candidatos.size(), creadas, omitidas);
    }

    /**
     * Al guardar la configuracion de un servidor o grupo en modalidad
     * automatica se planifica su ciclo de inmediato (RF27), despues de que la
     * configuracion quedo confirmada. Si no hay intervalo, lo reintenta el
     * proceso programado.
     */
    @TransactionalEventListener
    public void alActualizarConfiguracion(ConfiguracionMantenimientoActualizada evento) {
        try {
            List<Candidato> candidatos = Objects.requireNonNull(
                    transaccionNueva.execute(estado -> buscarCandidatos(evento.idServidor(), evento.idGrupo())));
            for (Candidato candidato : candidatos) {
                transaccionNueva.execute(estado -> generarCiclo(candidato));
            }
        } catch (ReglaNegocioException ex) {
            log.warn("Planificación inmediata pendiente tras configurar ({}): {}", evento, ex.getMessage());
        } catch (RuntimeException ex) {
            // La configuracion ya quedo confirmada; el proceso programado reintenta.
            log.error("Error al planificar tras configurar ({})", evento, ex);
        }
    }

    /**
     * DEC-18: al cambiar la ventana de un servidor, las ordenes programadas
     * que ya no caben en la nueva ventana se reprograman en la misma
     * transaccion. Si no hay intervalo, la orden se conserva y queda registrado.
     *
     * <p>Se ejecuta dentro de la transaccion de quien publica. Todo el trabajo
     * pasa por metodos privados: una excepcion capturada aqui no atraviesa un
     * proxy transaccional, asi que no marca la transaccion para deshacerse.
     */
    @EventListener
    public void alCambiarVentanas(VentanasServidorActualizadas evento) {
        Usuario autor = evento.actor().esSistema() ? null : usuarios.findById(evento.actor().usuarioId()).orElse(null);
        for (Orden orden : ordenes.findDelServidorEnEstados(evento.idServidor(), EnumSet.of(EstadoOrden.PROGRAMADA))) {
            if (sigueDentroDeSuVentana(orden)) {
                continue;
            }
            try {
                ResultadoPlanificacion resultado = reprogramarInterno(orden, null, "Cambio de ventana permisiva", autor);
                auditoria.registrar(evento.actor(), Operacion.de("REPROGRAMAR_ORDEN", "orden", orden.getId())
                        .sobreOrden(orden.getId()).sobreServidor(evento.idServidor())
                        .valores(null, instantanea(resultado))
                        .motivo("Cambio de ventana permisiva"));
            } catch (ReglaNegocioException ex) {
                log.warn("La orden {} quedó fuera de la nueva ventana y no pudo reprogramarse: {}",
                        orden.getCodigo(), ex.getMessage());
                auditoria.registrar(evento.actor(), Operacion.de("REPROGRAMACION_PENDIENTE", "orden", orden.getId())
                        .sobreOrden(orden.getId()).sobreServidor(evento.idServidor())
                        .motivo("Fuera de la nueva ventana permisiva: " + ex.getMessage()));
            }
        }
    }

    // ============================================================ internos

    /** Objetivos en modalidad automatica que necesitan su siguiente orden. */
    private List<Candidato> buscarCandidatos(Integer soloServidor, Integer soloGrupo) {
        List<Candidato> candidatos = new ArrayList<>();
        BigDecimal factorNormal = factor(ResultadoCiclo.ESTADO_NORMAL);

        if (soloGrupo == null) {
            for (ConfiguracionServidor config : configuracionesServidor.findByModalidadPlanificacion(ModalidadPlanificacion.AUTOMATICA)) {
                Servidor s = config.getServidor();
                if ((soloServidor != null && !soloServidor.equals(s.getId()))
                        || s.getEstado() != EstadoServidor.ACTIVO || s.getVentanas().isEmpty()
                        || ordenes.existsByServidorIdAndEstadoIn(s.getId(), OCUPAN)) {
                    continue;
                }
                siguienteFechaObjetivo(config, !ordenes.existsByServidorId(s.getId()),
                        cierres.findPendienteDeServidor(s.getId()), factorNormal)
                        .ifPresent(par -> candidatos.add(new Candidato(s.getId(), null, s.getNivelCriticidad().getPrioridad(),
                                par.fechaObjetivo(), par.idCierre(), "servidor " + s.getHostname())));
            }
        }
        if (soloServidor == null) {
            for (ConfiguracionGrupo config : configuracionesGrupo.findByModalidadPlanificacion(ModalidadPlanificacion.AUTOMATICA)) {
                Integer idGrupo = config.getGrupo().getId();
                if (soloGrupo != null && !soloGrupo.equals(idGrupo)) {
                    continue;
                }
                GrupoMantenimiento g = grupos.findConIntegrantesById(idGrupo).orElse(null);
                if (g == null || g.getEstado() != EstadoGrupo.ACTIVO || g.getIntegrantes().isEmpty()
                        || ordenes.existsByGrupoIdAndEstadoIn(idGrupo, OCUPAN)) {
                    continue;
                }
                int prioridad = g.criticidadEfectiva().map(NivelCriticidad::getPrioridad).orElse(Integer.MAX_VALUE);
                siguienteFechaObjetivo(config, !ordenes.existsByGrupoId(idGrupo),
                        cierres.findPendienteDeGrupo(idGrupo), factorNormal)
                        .ifPresent(par -> candidatos.add(new Candidato(null, idGrupo, prioridad, par.fechaObjetivo(),
                                par.idCierre(), "grupo " + g.getNombre())));
            }
        }
        return candidatos;
    }

    /**
     * RF64: primer ciclo desde la configuracion (DEC-09) o, si hubo un cierre
     * sin orden siguiente, desde ese cierre con su periodicidad y factor.
     * Si hay ordenes pero ninguna cerrada, el ciclo continua en curso.
     */
    private Optional<FechaYCierre> siguienteFechaObjetivo(ConfiguracionMantenimiento config, boolean sinOrdenes,
                                                          Optional<CierreOrden> cierre, BigDecimal factorNormal) {
        if (cierre.isPresent()) {
            CierreOrden c = cierre.get();
            return Optional.of(new FechaYCierre(
                    FechaObjetivo.calcular(c.getFechaCierre(), c.getPeriodicidadBaseDias(), c.getFactorAplicado()), c.getId()));
        }
        if (sinOrdenes) {
            return Optional.of(new FechaYCierre(
                    FechaObjetivo.calcular(config.getFechaCreacion(), config.getFrecuenciaMantenimientoDias(), factorNormal), null));
        }
        return Optional.empty();
    }

    private Orden generarCiclo(Candidato candidato) {
        Instant ahora = Tiempo.ahora(reloj);
        // Se vuelve a comprobar dentro de la transaccion: otra corrida pudo
        // haber generado la orden mientras tanto.
        boolean yaTieneOrden = candidato.idServidor() != null
                ? ordenes.existsByServidorIdAndEstadoIn(candidato.idServidor(), OCUPAN)
                : ordenes.existsByGrupoIdAndEstadoIn(candidato.idGrupo(), OCUPAN);
        if (yaTieneOrden) {
            return null;
        }
        Objetivo objetivo = objetivo(candidato.idServidor(), candidato.idGrupo());
        ResultadoPlanificacion resultado = planificador.planificar(
                solicitud(objetivo, candidato.fechaObjetivo(), ahora), reservas(ahora, candidato.fechaObjetivo(), null));
        Orden orden = crearOrden(objetivo, resultado, OrigenOrden.PLANIFICACION_AUTOMATICA, null,
                "Ciclo automático generado por el Sistema", ahora);
        if (candidato.idCierre() != null) {
            cierres.findById(candidato.idCierre()).ifPresent(c -> c.enlazarSiguiente(orden));
        }
        auditoria.registrar(Actor.sistema(PROCESO_AUTOMATICO), Operacion.de("GENERAR_ORDEN_AUTOMATICA", "orden", orden.getId())
                .sobreOrden(orden.getId())
                .sobreServidor(candidato.idServidor()).sobreGrupo(candidato.idGrupo())
                .valores(null, instantanea(resultado)));
        return orden;
    }

    private ResultadoPlanificacion reprogramarInterno(Orden orden, Instant inicio, String motivo, Usuario autor) {
        if (!orden.getEstado().puedePasarA(EstadoOrden.REPROGRAMADA)) {
            throw new ReglaNegocioException("La orden %s está en estado %s y no puede reprogramarse"
                    .formatted(orden.getCodigo(), orden.getEstado()));
        }
        Objetivo objetivo = objetivoDeOrden(orden);
        Instant ahora = Tiempo.ahora(reloj);
        Instant fechaObjetivo = orden.programacionVigente().map(ProgramacionOrden::getFechaObjetivo).orElse(ahora);
        SolicitudPlanificacion solicitud = solicitud(objetivo, fechaObjetivo, ahora);
        List<Reserva> reservas = reservas(ahora, inicio != null ? inicio : fechaObjetivo, orden.getId());
        ResultadoPlanificacion resultado = inicio != null
                ? planificador.verificar(solicitud, inicio, reservas)
                : planificador.planificar(solicitud, reservas);

        List<Orden.TramoDetalle> tramos = resultado.tramos().stream()
                .map(t -> new Orden.TramoDetalle(t.idServidor(), t.posicion(), t.inicio(), t.fin()))
                .toList();
        orden.reprogramar(datosProgramacion(resultado, motivo), tramos, autor, motivo, ahora);
        return resultado;
    }

    /** Verdadero si la programacion vigente sigue completamente dentro de la ventana actual. */
    private boolean sigueDentroDeSuVentana(Orden orden) {
        Optional<ProgramacionOrden> vigente = orden.programacionVigente();
        if (vigente.isEmpty()) {
            return true;
        }
        Intervalo programada = new Intervalo(vigente.get().getFechaInicioProgramada(), vigente.get().getFechaFinProgramada());
        CalendarioSemanal ventana = objetivoDeOrden(orden).ventana();
        return ventana.proyectar(programada.inicio().minus(Duration.ofDays(8)), programada.fin().plus(Duration.ofDays(1)),
                        propiedades.zona())
                .stream().anyMatch(v -> v.contiene(programada));
    }

    private Objetivo objetivo(Integer idServidor, Integer idGrupo) {
        if ((idServidor == null) == (idGrupo == null)) {
            throw new ReglaNegocioException("Indique un servidor o un grupo, no ambos");
        }
        return idServidor != null ? objetivoDeServidor(idServidor) : objetivoDeGrupo(idGrupo);
    }

    /** HU06 CA3 / HU13 CA3: solo servidores activos con configuracion generan mantenimientos. */
    private Objetivo objetivoDeServidor(Integer id) {
        Servidor s = servidores.findConDetalleById(id).orElseThrow(() -> RecursoNoEncontradoException.de("el servidor", id));
        if (s.getEstado() != EstadoServidor.ACTIVO) {
            throw new ReglaNegocioException(s.estaDadoDeBaja()
                    ? "El servidor %s está dado de baja y no admite mantenimientos".formatted(s.getHostname())
                    : "El servidor %s está pendiente de configuración".formatted(s.getHostname()));
        }
        ConfiguracionServidor config = configuracionesServidor.findByServidorId(id)
                .orElseThrow(() -> new ReglaNegocioException("El servidor %s no tiene configuración de mantenimiento"
                        .formatted(s.getHostname())));
        return new Objetivo(s, null, List.of(s), s.getNivelCriticidad(), null, CalendarioSemanal.de(s.getVentanas()), config);
    }

    private Objetivo objetivoDeGrupo(Integer id) {
        GrupoMantenimiento g = grupos.findConIntegrantesById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.de("el grupo de mantenimiento", id));
        if (g.getEstado() != EstadoGrupo.ACTIVO) {
            throw new ReglaNegocioException("El grupo %s no está activo".formatted(g.getNombre()));
        }
        List<Servidor> integrantes = g.servidores().stream()
                .filter(s -> !s.estaDadoDeBaja())
                .sorted(Comparator.comparing(Servidor::getHostname))
                .toList();
        if (integrantes.isEmpty()) {
            throw new ReglaNegocioException("El grupo %s no tiene integrantes".formatted(g.getNombre()));
        }
        ConfiguracionGrupo config = configuracionesGrupo.findByGrupoId(id)
                .orElseThrow(() -> new ReglaNegocioException("El grupo %s no tiene configuración de mantenimiento"
                        .formatted(g.getNombre())));
        NivelCriticidad criticidad = g.criticidadEfectiva().orElseThrow();
        return new Objetivo(null, g, integrantes, criticidad, config.getModoEjecucion(),
                ServicioGrupo.ventanaEfectiva(g), config);
    }

    /**
     * Para reprogramar se usan los valores con que se genero la orden
     * (criticidad, modo, integrantes; RF20, HU13 CA8) y la ventana vigente de
     * esos servidores, porque los cambios de ventana aplican a los proximos
     * mantenimientos (HU14 CA3).
     */
    private Objetivo objetivoDeOrden(Orden orden) {
        List<Integer> ids = orden.getDetalles().stream()
                .filter(d -> d.getEstado().ocupaServidor())
                .sorted(Comparator.comparingInt(OrdenDetalle::getPosicionEjecucion))
                .map(d -> d.getServidor().getId())
                .toList();
        if (ids.isEmpty()) {
            throw new ReglaNegocioException("La orden %s no tiene servidores pendientes".formatted(orden.getCodigo()));
        }
        Map<Integer, Servidor> porId = servidores.findConDetalleByIdIn(ids).stream()
                .collect(Collectors.toMap(Servidor::getId, Function.identity()));
        List<Servidor> integrantes = ids.stream().map(porId::get).toList();
        CalendarioSemanal ventana = CalendarioSemanal.interseccionDe(
                integrantes.stream().map(s -> CalendarioSemanal.de(s.getVentanas())).toList());
        return new Objetivo(orden.getServidor(), orden.getGrupo(), integrantes, orden.getNivelCriticidad(),
                orden.esGrupal() ? orden.getModoEjecucionAplicado() : null, ventana, null);
    }

    private SolicitudPlanificacion solicitud(Objetivo o, Instant fechaObjetivo, Instant ahora) {
        ParametrosSistema p = parametros.obtener();
        return new SolicitudPlanificacion(
                o.integrantes().stream().map(Servidor::getId).toList(),
                o.grupo() != null ? (o.modo() == null ? ModoEjecucion.SECUENCIAL : o.modo()) : null,
                fechaObjetivo,
                Duration.ofHours(o.criticidad().getPlazoAutorizacionHoras()),
                o.ventana(),
                Duration.ofMinutes(p.getMaxDuracionMopMinutos()),
                p.getMaxEjecucionesConcurrentes(),
                ahora,
                propiedades.zona(),
                propiedades.horizonte());
    }

    /** Reservas de las ordenes que ocupan el cronograma, sin las de la orden que se replanifica. */
    private List<Reserva> reservas(Instant ahora, Instant fechaObjetivo, Integer excluirOrden) {
        Instant desde = (fechaObjetivo.isBefore(ahora) ? fechaObjetivo : ahora).minus(Duration.ofDays(1));
        Instant hasta = (fechaObjetivo.isAfter(ahora) ? fechaObjetivo : ahora)
                .plus(propiedades.horizonte()).plus(Duration.ofDays(30));
        return detalles.findReservas(OCUPAN, EstadoDetalleOrden.NO_INICIADO, desde, hasta).stream()
                .filter(f -> excluirOrden == null || !excluirOrden.equals(f.idOrden()))
                .map(f -> new Reserva(f.idServidor(), new Intervalo(f.inicio(), f.fin()), f.idOrden(), f.codigoOrden()))
                .toList();
    }

    private Orden crearOrden(Objetivo o, ResultadoPlanificacion r, OrigenOrden origen, Usuario solicitante,
                             String motivo, Instant ahora) {
        Integer idCuenta = o.configuracion().getIdCuentaServicio() != null
                ? o.configuracion().getIdCuentaServicio()
                : parametros.obtener().getIdCuentaServicioPredeterminada();
        Orden orden = Orden.nueva(ordenes.siguienteId(), o.servidor(), o.grupo(), o.criticidad(), idCuenta,
                solicitante, origen, o.modo(), ahora, motivo);
        Map<Integer, Servidor> porId = new HashMap<>();
        o.integrantes().forEach(s -> porId.put(s.getId(), s));
        for (ResultadoPlanificacion.Tramo t : r.tramos()) {
            orden.agregarDetalle(porId.get(t.idServidor()), t.posicion(), t.inicio(), t.fin());
        }
        orden.agregarProgramacion(datosProgramacion(r, motivo), solicitante, ahora);
        return ordenes.save(orden);
    }

    private static ProgramacionOrden.Datos datosProgramacion(ResultadoPlanificacion r, String motivo) {
        return new ProgramacionOrden.Datos(r.fechaObjetivo(), r.inicio(), r.fin(), r.fechaEvaluacion(),
                r.ventanaAplicada().inicio(), r.ventanaAplicada().fin(), motivo);
    }

    private Orden buscarOrden(Integer id) {
        return ordenes.findConDetalleById(id).orElseThrow(() -> RecursoNoEncontradoException.de("la orden", id));
    }

    private BigDecimal factor(ResultadoCiclo resultado) {
        return factores.findByResultado(resultado).map(FactorCiclo::getFactor)
                .orElseThrow(() -> new ReglaNegocioException("Falta el factor de ciclo " + resultado));
    }

    private Usuario usuarioDe(Actor actor) {
        return actor.esSistema() ? null : usuarios.findById(actor.usuarioId())
                .orElseThrow(() -> RecursoNoEncontradoException.de("el usuario", actor.usuarioId()));
    }

    private static String textoOPorDefecto(String texto, String porDefecto) {
        return texto == null || texto.isBlank() ? porDefecto : texto.trim();
    }

    private static Map<String, Object> instantanea(ResultadoPlanificacion r) {
        return Map.of("fechaObjetivo", r.fechaObjetivo().toString(), "inicio", r.inicio().toString(),
                "fin", r.fin().toString(), "fechaEvaluacion", r.fechaEvaluacion().toString());
    }

    // ============================================================ tipos

    /** Objetivo de una orden con los valores que alimentan el algoritmo. */
    private record Objetivo(Servidor servidor, GrupoMantenimiento grupo, List<Servidor> integrantes,
                            NivelCriticidad criticidad, ModoEjecucion modo, CalendarioSemanal ventana,
                            ConfiguracionMantenimiento configuracion) {
    }

    private record Candidato(Integer idServidor, Integer idGrupo, int prioridad, Instant fechaObjetivo,
                             Integer idCierre, String descripcion) {
    }

    private record FechaYCierre(Instant fechaObjetivo, Integer idCierre) {
    }

    /** Resultado de una corrida de la planificacion automatica. */
    public record ResumenPlanificacion(int evaluados, List<String> ordenesGeneradas, List<String> sinIntervalo) {
    }
}
