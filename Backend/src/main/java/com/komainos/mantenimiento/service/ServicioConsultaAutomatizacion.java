package com.komainos.mantenimiento.service;

import com.komainos.ejecucion.config.PropiedadesAutomatizacion;
import com.komainos.inventario.service.PuertoCuentasServicio;
import com.komainos.inventario.service.ServicioParametrosSistema;
import com.komainos.mantenimiento.model.AutorizacionDetalle;
import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.EstadoTareaMop;
import com.komainos.mantenimiento.model.EtapaOrden;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.HistorialEstadoOrden;
import com.komainos.mantenimiento.model.Incidencia;
import com.komainos.mantenimiento.model.Mop;
import com.komainos.mantenimiento.model.MopTarea;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.OrigenOrden;
import com.komainos.mantenimiento.model.SolicitudAutorizacion;
import com.komainos.mantenimiento.model.TipoAprobador;
import com.komainos.mantenimiento.model.ValidacionFuncional;
import com.komainos.mantenimiento.model.ValidacionTecnica;
import com.komainos.mantenimiento.repository.EvaluacionRepositorio;
import com.komainos.mantenimiento.repository.IncidenciaRepositorio;
import com.komainos.mantenimiento.repository.MopRepositorio;
import com.komainos.mantenimiento.repository.MopTareaRepositorio;
import com.komainos.mantenimiento.repository.OrdenDetalleRepositorio;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.mantenimiento.repository.SolicitudAutorizacionRepositorio;
import com.komainos.mantenimiento.repository.ValidacionFuncionalRepositorio;
import com.komainos.mantenimiento.repository.ValidacionTecnicaRepositorio;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.seguridad.model.Rol;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consultas del ciclo para el panel (RF33-RF36, RF53, RF54): la ficha de automatización de una orden con su bitácora,
 * la sala de operaciones, la cola y el historial de ejecución de los scripts (R3.4). El responsable solo ve lo de sus
 * servidores; el filtro se aplica en la consulta.
 */
@Service
@RequiredArgsConstructor
public class ServicioConsultaAutomatizacion {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Lima"));

    private final ServicioConsultaOrdenes consulta;
    private final OrdenRepositorio ordenes;
    private final OrdenDetalleRepositorio detalles;
    private final MopRepositorio mops;
    private final MopTareaRepositorio tareas;
    private final EvaluacionRepositorio evaluaciones;
    private final SolicitudAutorizacionRepositorio solicitudes;
    private final ValidacionFuncionalRepositorio funcionales;
    private final ValidacionTecnicaRepositorio tecnicas;
    private final IncidenciaRepositorio incidencias;
    private final PuertoCuentasServicio cuentas;
    private final ServicioParametrosSistema parametros;
    private final PropiedadesAutomatizacion propiedades;

    @Transactional(readOnly = true)
    public FichaAutomatizacion ficha(Integer idOrden, AlcanceUsuario alcance) {
        Orden orden = consulta.obtener(idOrden, alcance);
        Set<Integer> visibles = orden.getDetalles().stream()
                .filter(d -> alcance.veTodoElInventario() || d.getServidor().esVisiblePara(alcance.usuarioId()))
                .map(OrdenDetalle::getId)
                .collect(Collectors.toSet());

        List<Mop> planes = mops.findByDetalleOrdenIdOrderByDetallePosicionEjecucionAsc(idOrden).stream()
                .filter(m -> visibles.contains(m.getDetalle().getId()))
                .toList();
        planes.forEach(m -> m.getTareas().forEach(t -> {
            Hibernate.initialize(t.getTarea());
            if (t.getVersion() != null) {
                Hibernate.initialize(t.getVersion().getImplementacion().getScript());
            }
        }));
        List<Evaluacion> analisis = visibles.isEmpty() ? List.of() : evaluaciones.findByDetalleIdInOrderByFechaInicioAsc(visibles);
        analisis.forEach(ServicioEvaluaciones::cargar);
        List<SolicitudAutorizacion> rondas = solicitudes.findByOrdenIdOrderByNumeroRondaAsc(idOrden);
        rondas.forEach(s -> s.getDecisiones().forEach(d -> Hibernate.initialize(d.getUsuarioDecision())));
        Optional<ValidacionFuncional> validacion = funcionales.findByOrdenId(idOrden)
                .filter(v -> alcance.veTodoElInventario() || v.getResponsable().getId().equals(alcance.usuarioId()));
        List<ValidacionTecnica> validacionesTecnicas = tecnicas.findByDetalleOrdenId(idOrden).stream()
                .filter(v -> visibles.contains(v.getDetalle().getId()))
                .toList();
        List<Incidencia> problemas = incidencias.findByDetalleOrdenIdOrderByFechaAperturaAsc(idOrden).stream()
                .filter(i -> visibles.contains(i.getDetalle().getId()))
                .toList();

        Map<Integer, String> nombresCuenta = new LinkedHashMap<>();
        planes.stream().flatMap(m -> m.getTareas().stream()).map(MopTarea::getIdCuentaServicio)
                .filter(Objects::nonNull).distinct()
                .forEach(id -> cuentas.nombre(id).ifPresent(nombre -> nombresCuenta.put(id, nombre)));
        analisis.stream().map(Evaluacion::getIdCuentaServicio).distinct()
                .forEach(id -> cuentas.nombre(id).ifPresent(nombre -> nombresCuenta.put(id, nombre)));

        SolicitudAutorizacion vigente = rondas.isEmpty() ? null : rondas.getLast();
        return new FichaAutomatizacion(orden, planes, analisis, rondas, validacion, validacionesTecnicas, problemas,
                nombresCuenta, propiedades.simulada(), parametros.obtener().getMaxDuracionMopMinutos(),
                acciones(orden, vigente, validacion, alcance), siguientePaso(orden, planes, vigente, validacion));
    }

    @Transactional(readOnly = true)
    public Page<Orden> sala(EtapaOrden etapa, Integer idEntorno, Integer idCriticidad, String texto, Pageable pagina) {
        Specification<Orden> filtro = (raiz, consultaCriteria, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (etapa != null) {
                condiciones.add(raiz.get("estado").in(Arrays.stream(EstadoOrden.values())
                        .filter(e -> e.etapa() == etapa).toList()));
            }
            if (idCriticidad != null) {
                condiciones.add(cb.equal(raiz.get("nivelCriticidad").get("id"), idCriticidad));
            }
            if (idEntorno != null) {
                Subquery<Integer> sub = consultaCriteria.subquery(Integer.class);
                var d = sub.from(OrdenDetalle.class);
                sub.select(d.get("id")).where(cb.equal(d.get("orden"), raiz),
                        cb.equal(d.get("servidor").get("entorno").get("id"), idEntorno));
                condiciones.add(cb.exists(sub));
            }
            if (texto != null && !texto.isBlank()) {
                String patron = "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
                Subquery<Integer> sub = consultaCriteria.subquery(Integer.class);
                var d = sub.from(OrdenDetalle.class);
                sub.select(d.get("id")).where(cb.equal(d.get("orden"), raiz),
                        cb.like(cb.lower(d.get("servidor").get("hostname")), patron));
                condiciones.add(cb.or(cb.like(cb.lower(raiz.get("codigo")), patron), cb.exists(sub)));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        Page<Orden> resultado = ordenes.findAll(filtro, pagina);
        resultado.forEach(o -> Hibernate.initialize(o.getDetalles()));
        return resultado;
    }

    /** RF35: pendientes y en ejecución, primero los de prioridad alta */
    @Transactional(readOnly = true)
    public Page<OrdenDetalle> cola(Pageable pagina) {
        return detalles.cola(EnumSet.of(EstadoDetalleOrden.EN_COLA, EstadoDetalleOrden.EN_EJECUCION),
                EnumSet.of(OrigenOrden.LANZAMIENTO_INMEDIATO, OrigenOrden.CONDICION_CRITICA), pagina);
    }

    /** RF34: órdenes por etapa del ciclo */
    @Transactional(readOnly = true)
    public Map<EtapaOrden, Long> indicadores() {
        Map<EtapaOrden, Long> cantidades = new LinkedHashMap<>();
        for (EtapaOrden etapa : EtapaOrden.values()) {
            List<EstadoOrden> estados = Arrays.stream(EstadoOrden.values()).filter(e -> e.etapa() == etapa).toList();
            cantidades.put(etapa, ordenes.count((raiz, c, cb) -> raiz.get("estado").in(estados)));
        }
        return cantidades;
    }

    /** RF26: tareas fijadas o ejecutadas con una versión del script */
    @Transactional(readOnly = true)
    public Page<MopTarea> usosVersion(Integer idVersion, Pageable pagina) {
        return tareas.findByVersionIdOrderByIdDesc(idVersion, pagina);
    }

    /** R3.4: cada ejecución de una tarea de MOP con su versión, cuenta, código de retorno y duración */
    @Transactional(readOnly = true)
    public Page<MopTarea> ejecuciones(FiltroEjecuciones filtro, Pageable pagina) {
        Specification<MopTarea> especificacion = (raiz, c, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.isNotNull(raiz.get("fechaInicio")));
            if (filtro.idServidor() != null) {
                condiciones.add(cb.equal(raiz.get("mop").get("detalle").get("servidor").get("id"), filtro.idServidor()));
            }
            if (filtro.idScript() != null) {
                condiciones.add(cb.equal(raiz.get("version").get("implementacion").get("script").get("id"), filtro.idScript()));
            }
            if (filtro.estado() != null) {
                condiciones.add(cb.equal(raiz.get("estado"), filtro.estado()));
            }
            if (filtro.desde() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(raiz.get("fechaInicio"), filtro.desde()));
            }
            if (filtro.hasta() != null) {
                condiciones.add(cb.lessThan(raiz.get("fechaInicio"), filtro.hasta()));
            }
            if (filtro.texto() != null && !filtro.texto().isBlank()) {
                String patron = "%" + filtro.texto().trim().toLowerCase(Locale.ROOT) + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(raiz.get("mop").get("detalle").get("orden").get("codigo")), patron),
                        cb.like(cb.lower(raiz.get("mop").get("detalle").get("servidor").get("hostname")), patron),
                        cb.like(cb.lower(raiz.get("tarea").get("nombre")), patron)));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        return tareas.findAll(especificacion, PageRequest.of(pagina.getPageNumber(), pagina.getPageSize(),
                Sort.by(Sort.Order.desc("fechaInicio"), Sort.Order.desc("id"))));
    }

    @Transactional(readOnly = true)
    public MopTarea ejecucion(Integer idTareaMop) {
        return tareas.findConDetalleById(idTareaMop)
                .orElseThrow(() -> RecursoNoEncontradoException.de("la ejecución", idTareaMop));
    }

    @Transactional(readOnly = true)
    public Optional<String> nombreCuenta(Integer idCuenta) {
        return idCuenta == null ? Optional.empty() : cuentas.nombre(idCuenta);
    }

    /** Las acciones que el usuario puede hacer ahora; el servicio de cada acción las vuelve a comprobar */
    private Acciones acciones(Orden orden, SolicitudAutorizacion ronda, Optional<ValidacionFuncional> validacion,
                              AlcanceUsuario alcance) {
        boolean admin = alcance.esAdministrador();
        boolean responsable = alcance.rol() == Rol.RESPONSABLE
                && orden.getDetalles().stream().allMatch(d -> d.getServidor().esVisiblePara(alcance.usuarioId()));
        EstadoOrden estado = orden.getEstado();
        boolean enAutorizacion = estado == EstadoOrden.PENDIENTE_AUTORIZACION && ronda != null;
        Optional<AutorizacionDetalle> pendiente = enAutorizacion ? ronda.pendienteActual() : Optional.empty();
        boolean hayPropuesta = enAutorizacion && ronda.propuesta().isPresent();
        boolean turnoResponsable = pendiente.map(d -> d.getTipo() == TipoAprobador.RESPONSABLE).orElse(false) && responsable;
        boolean turnoAdministrador = pendiente.map(d -> d.getTipo() == TipoAprobador.ADMINISTRADOR).orElse(false) && admin;
        boolean validar = estado == EstadoOrden.PENDIENTE_VALIDACION && validacion
                .filter(v -> v.estaPendiente() && v.getResponsable().getId().equals(alcance.usuarioId())).isPresent();
        return new Acciones(
                admin && estado == EstadoOrden.PROGRAMADA,
                turnoResponsable || turnoAdministrador,
                turnoResponsable,
                admin && hayPropuesta,
                validar,
                admin && estado == EstadoOrden.SIN_IMPLEMENTACION,
                admin && EnumSet.of(EstadoOrden.AUTORIZADA, EstadoOrden.EN_COLA, EstadoOrden.EN_EJECUCION).contains(estado));
    }

    /** Qué espera la orden para avanzar, en una frase para el panel */
    private String siguientePaso(Orden orden, List<Mop> planes, SolicitudAutorizacion ronda,
                                 Optional<ValidacionFuncional> validacion) {
        return switch (orden.getEstado()) {
            case PROGRAMADA, REPROGRAMADA -> "La evaluación previa inicia el %s y la ejecución el %s."
                    .formatted(FECHA.format(orden.getFechaEvaluacionProgramada()), FECHA.format(orden.getFechaInicioProgramada()));
            case EN_EVALUACION -> "Analizando el estado de los servidores con el script de análisis…";
            // El motivo registrado ya empieza con «Ejecución bloqueada»
            case SIN_IMPLEMENTACION -> "Ejecución bloqueada: "
                    + ultimoMotivo(orden).replaceFirst("^Ejecución bloqueada: ", "")
                    + ". Corrija el catálogo o la configuración y cierre la orden sin intervención.";
            case PENDIENTE_AUTORIZACION -> {
                if (ronda == null) {
                    yield "Esperando la solicitud de autorización.";
                }
                if (ronda.propuesta().isPresent()) {
                    yield "El responsable propuso iniciar el %s: el administrador debe aceptarla o rechazar el mantenimiento."
                            .formatted(FECHA.format(ronda.propuesta().get().getFechaPropuesta()));
                }
                yield ronda.pendienteActual()
                        .map(d -> "Esperando la aprobación del %s hasta el %s.".formatted(
                                d.getTipo() == TipoAprobador.RESPONSABLE ? "responsable" : "administrador",
                                FECHA.format(ronda.getFechaLimite())))
                        .orElse("Aprobaciones en proceso.");
            }
            case AUTORIZADA -> "Autorizada: entra en la cola el %s.".formatted(FECHA.format(orden.getFechaInicioProgramada()));
            case EN_COLA -> "En cola: inicia cuando haya capacidad y el servidor esté libre.";
            case EN_EJECUCION -> {
                long total = planes.stream().mapToLong(m -> m.getTareas().size()).sum();
                long hechas = planes.stream().flatMap(m -> m.getTareas().stream())
                        .filter(t -> t.getEstado() == EstadoTareaMop.FINALIZADA).count();
                yield "Ejecutando: %d de %d tareas completadas.".formatted(hechas, total);
            }
            case PENDIENTE_VALIDACION -> validacion
                    .map(v -> "Esperando la conformidad de %s hasta el %s.".formatted(v.getResponsable().getNombreCompleto(),
                            FECHA.format(v.getFechaLimite())))
                    .orElse("Esperando la validación funcional.");
            case INCIDENCIA -> "Incidencia abierta: atiéndala y ciérrela para terminar el ciclo.";
            case RECHAZADA, AUTORIZACION_VENCIDA, VALIDACION_VENCIDA, CANCELADA -> "Cerrando el ciclo…";
            case CERRADA -> "Ciclo cerrado: " + ultimoMotivo(orden) + ".";
        };
    }

    private static String ultimoMotivo(Orden orden) {
        return orden.getHistorial().stream()
                .reduce((a, b) -> b)
                .map(HistorialEstadoOrden::getMotivo)
                .filter(m -> m != null && !m.isBlank())
                .orElse("sin motivo registrado");
    }

    public record FiltroEjecuciones(Integer idServidor, Integer idScript, EstadoTareaMop estado, Instant desde,
                                    Instant hasta, String texto) {
    }

    /**
     * @param evaluar          el administrador inicia la evaluación previa de una orden programada
     * @param decidir          aprobar o rechazar en su turno de la secuencia (RF44)
     * @param proponerFecha    solo el responsable, en su turno (RF48)
     * @param decidirPropuesta el administrador acepta la fecha propuesta o rechaza el mantenimiento
     * @param validar          el responsable registra su conformidad o rechazo (RF57)
     */
    public record Acciones(boolean evaluar, boolean decidir, boolean proponerFecha, boolean decidirPropuesta,
                           boolean validar, boolean cerrarSinImplementacion, boolean procesarCola) {
    }

    /**
     * @param cuentas nombre de cada cuenta de servicio usada, sin secretos
     */
    public record FichaAutomatizacion(Orden orden, List<Mop> mops, List<Evaluacion> evaluaciones,
                                      List<SolicitudAutorizacion> autorizaciones, Optional<ValidacionFuncional> validacion,
                                      List<ValidacionTecnica> validacionesTecnicas, List<Incidencia> incidencias,
                                      Map<Integer, String> cuentas, boolean simulado, int maxDuracionMop,
                                      Acciones acciones, String siguientePaso) {
    }
}
