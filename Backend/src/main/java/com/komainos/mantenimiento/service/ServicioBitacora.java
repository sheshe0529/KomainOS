package com.komainos.mantenimiento.service;

import com.komainos.inventario.model.NivelAlerta;
import com.komainos.inventario.model.Servidor;
import com.komainos.inventario.service.ServicioGrupo;
import com.komainos.inventario.service.ServicioServidor;
import com.komainos.mantenimiento.model.AutorizacionDetalle;
import com.komainos.mantenimiento.model.EstadoAutorizacion;
import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoEvaluacion;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.EstadoTareaMop;
import com.komainos.mantenimiento.model.EstadoValidacionFuncional;
import com.komainos.mantenimiento.model.EtapaOrden;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.HistorialEstadoOrden;
import com.komainos.mantenimiento.model.Incidencia;
import com.komainos.mantenimiento.model.MedicionMetrica;
import com.komainos.mantenimiento.model.Mop;
import com.komainos.mantenimiento.model.MopTarea;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.OrigenOrden;
import com.komainos.mantenimiento.model.PrioridadOrden;
import com.komainos.mantenimiento.model.ResultadoValidacionTecnica;
import com.komainos.mantenimiento.model.SolicitudAutorizacion;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.model.ValidacionTecnica;
import com.komainos.mantenimiento.repository.BitacoraRepositorio;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.mantenimiento.service.ServicioConsultaAutomatizacion.FichaAutomatizacion;
import com.komainos.seguridad.model.AlcanceUsuario;
import com.komainos.shared.exception.RecursoNoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Bitácora de mantenimiento (RF53) de una orden, de un servidor o de un grupo: los eventos de cada ciclo en orden
 * cronológico, para exportarlos, y un resumen por mantenimiento para la vista gráfica.
 */
@Service
@RequiredArgsConstructor
public class ServicioBitacora {

    /** Tope por consulta: la bitácora de un servidor con mantenimiento mensual cubre ocho años */
    public static final int MAXIMO_MANTENIMIENTOS = 100;

    // Cada participación agrega un parámetro a la consulta, incluso si todas pertenecen a una misma orden.
    private static final int PARTICIPACIONES_POR_LOTE = 10_000;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Lima"));
    private static final Set<EstadoOrden> SIN_AUTORIZAR = EnumSet.of(EstadoOrden.RECHAZADA,
            EstadoOrden.AUTORIZACION_VENCIDA);
    private static final Set<EstadoOrden> A_ATENDER = EnumSet.of(EstadoOrden.SIN_IMPLEMENTACION,
            EstadoOrden.INCIDENCIA, EstadoOrden.VALIDACION_VENCIDA);
    private static final Set<EstadoOrden> PROGRAMADAS = EnumSet.of(EstadoOrden.PROGRAMADA, EstadoOrden.REPROGRAMADA);

    private final ServicioConsultaAutomatizacion consulta;
    private final ServicioServidor servidores;
    private final ServicioGrupo grupos;
    private final OrdenRepositorio ordenes;
    private final BitacoraRepositorio bitacora;

    @Transactional(readOnly = true)
    public EventosBitacora eventosDeOrden(Integer idOrden, AlcanceUsuario alcance) {
        FichaAutomatizacion ficha = consulta.ficha(idOrden, alcance);
        return new EventosBitacora(ficha.orden().getCodigo(), eventos(ficha, null));
    }

    /** Todos los mantenimientos en que participó el servidor, individuales o de un grupo */
    @Transactional(readOnly = true)
    public Bitacora delServidor(Integer idServidor, AlcanceUsuario alcance) {
        Servidor servidor = servidores.obtener(idServidor, alcance);
        List<Integer> ids = ordenes.findIdsDelServidor(idServidor, PageRequest.of(0, MAXIMO_MANTENIMIENTOS + 1));
        return new Bitacora(servidor.getHostname(), mantenimientos(ids, idServidor, alcance),
                ids.size() > MAXIMO_MANTENIMIENTOS);
    }

    @Transactional(readOnly = true)
    public EventosBitacora eventosDelServidor(Integer idServidor, AlcanceUsuario alcance) {
        Servidor servidor = servidores.obtener(idServidor, alcance);
        List<Integer> ids = ordenes.findIdsDelServidor(idServidor, PageRequest.of(0, MAXIMO_MANTENIMIENTOS));
        return new EventosBitacora(servidor.getHostname(), cronologia(fichas(ids, alcance), idServidor));
    }

    /** El acceso al grupo y el alcance de sus participaciones se leen en la misma instantánea. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Bitacora delGrupo(Integer idGrupo, AlcanceUsuario alcance) {
        String nombre = grupos.visible(idGrupo, alcance).getNombre();
        List<Integer> ids = ordenes.findIdsDelGrupo(idGrupo, alcance.veTodoElInventario(), alcance.usuarioId(),
                PageRequest.of(0, MAXIMO_MANTENIMIENTOS + 1));
        return new Bitacora(nombre, mantenimientos(ids, null, alcance),
                ids.size() > MAXIMO_MANTENIMIENTOS);
    }

    /** @param idServidor solo los eventos de ese integrante y los de la orden; nulo para todo el grupo */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public EventosBitacora eventosDelGrupo(Integer idGrupo, Integer idServidor, AlcanceUsuario alcance) {
        String nombre = grupos.ficha(idGrupo, alcance).grupo().getNombre();
        if (idServidor != null) {
            nombre += " " + servidores.obtener(idServidor, alcance).getHostname();
        }
        List<Integer> ids = ordenes.findIdsDelGrupo(idGrupo, alcance.veTodoElInventario(), alcance.usuarioId(),
                PageRequest.of(0, MAXIMO_MANTENIMIENTOS));
        return new EventosBitacora(nombre, cronologia(fichas(ids, alcance), idServidor));
    }

    private List<FichaAutomatizacion> fichas(List<Integer> ids, AlcanceUsuario alcance) {
        return ids.stream().limit(MAXIMO_MANTENIMIENTOS).map(id -> consulta.ficha(id, alcance)).toList();
    }

    /** Las órdenes llegan de la más reciente a la más antigua; los eventos se leen al revés, como una bitácora */
    private static List<EventoBitacora> cronologia(List<FichaAutomatizacion> fichas, Integer idServidor) {
        return fichas.stream()
                .flatMap(f -> eventos(f, idServidor).stream())
                .sorted(Comparator.comparing(EventoBitacora::fecha, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    // ---------------------------------------------------------------- Eventos

    /**
     * Estados, análisis, decisiones, tareas, validaciones e incidencias de la orden. Con un servidor se quitan los
     * eventos de los demás integrantes y se conservan los de la orden, que no tienen servidor.
     */
    static List<EventoBitacora> eventos(FichaAutomatizacion f, Integer idServidor) {
        String codigo = f.orden().getCodigo();
        List<EventoBitacora> eventos = new ArrayList<>();

        for (HistorialEstadoOrden h : f.orden().getHistorial()) {
            eventos.add(new EventoBitacora(h.getFechaHora(), codigo, "Estado de la orden", null,
                    (h.getEstadoAnterior() == null ? "" : etiqueta(h.getEstadoAnterior()) + " → ")
                            + etiqueta(h.getEstadoNuevo()),
                    null, h.getUsuario() == null ? "Sistema" : h.getUsuario().getNombreCompleto(), h.getMotivo()));
        }

        for (Evaluacion e : f.evaluaciones()) {
            if (!delServidor(e.getServidor().getId(), idServidor)) {
                continue;
            }
            String cuenta = f.cuentas().get(e.getIdCuentaServicio());
            eventos.add(new EventoBitacora(e.getFechaInicio(), codigo, etiqueta(e.getTipo()), e.getServidor().getHostname(),
                    "%s v%d%s".formatted(e.getVersion().getImplementacion().getScript().getNombre(),
                            e.getVersion().getNumeroVersion(), cuenta == null ? "" : " con " + cuenta),
                    unir(etiqueta(e.getEstado()), e.estaFinalizada() ? etiqueta(e.nivel()) : null),
                    null, e.getError() != null ? e.getError() : mediciones(e.getMediciones())));
        }

        for (SolicitudAutorizacion s : f.autorizaciones()) {
            for (AutorizacionDetalle d : s.getDecisiones()) {
                Instant fecha = d.getFechaDecision() != null ? d.getFechaDecision() : d.getFechaSolicitud();
                if (fecha == null) {
                    continue;
                }
                eventos.add(new EventoBitacora(fecha, codigo, "Autorización (ronda %d)".formatted(s.getNumeroRonda()), null,
                        switch (d.getTipo()) {
                            case RESPONSABLE -> "Responsable";
                            case ADMINISTRADOR -> "Administrador";
                        },
                        etiqueta(d.getEstado()),
                        d.getUsuarioDecision() == null ? null : d.getUsuarioDecision().getNombreCompleto(),
                        unir(d.getMotivo(), d.getFechaPropuesta() == null ? null
                                : "Fecha propuesta: " + FECHA.format(d.getFechaPropuesta()))));
            }
        }

        for (Mop m : f.mops()) {
            Servidor servidor = m.getDetalle().getServidor();
            if (!delServidor(servidor.getId(), idServidor)) {
                continue;
            }
            for (MopTarea t : m.getTareas()) {
                if (t.getFechaInicio() == null) {
                    continue;
                }
                var v = t.getVersion();
                eventos.add(new EventoBitacora(t.getFechaInicio(), codigo, "Tarea", servidor.getHostname(),
                        "%d. %s%s".formatted(t.getOrdenEjecucion(), t.getTarea().getNombre(), v == null ? ""
                                : " · %s v%d".formatted(v.getImplementacion().getScript().getNombre(), v.getNumeroVersion())),
                        etiqueta(t.getEstado()) + (t.getCodigoRetorno() == null ? ""
                                : " (código %d)".formatted(t.getCodigoRetorno())),
                        f.cuentas().get(t.getIdCuentaServicio()),
                        t.getError() != null ? t.getError() : t.getSalida()));
            }
        }

        for (ValidacionTecnica v : f.validacionesTecnicas()) {
            Servidor servidor = v.getDetalle().getServidor();
            if (!delServidor(servidor.getId(), idServidor)) {
                continue;
            }
            eventos.add(new EventoBitacora(v.getFechaValidacion(), codigo, "Validación técnica", servidor.getHostname(),
                    "Análisis posterior contra los umbrales",
                    v.getResultado() == ResultadoValidacionTecnica.CONFORME ? "Conforme" : "No conforme",
                    null, v.getObservaciones()));
        }

        f.validacion().filter(v -> v.getFechaRespuesta() != null).ifPresent(v ->
                eventos.add(new EventoBitacora(v.getFechaRespuesta(), codigo, "Validación funcional", null,
                        "Conformidad del responsable", etiqueta(v.getEstado()),
                        v.getResponsable().getNombreCompleto(), v.getObservaciones())));

        for (Incidencia i : f.incidencias()) {
            Servidor servidor = i.getDetalle().getServidor();
            if (!delServidor(servidor.getId(), idServidor)) {
                continue;
            }
            eventos.add(new EventoBitacora(i.getFechaApertura(), codigo, "Incidencia", servidor.getHostname(),
                    switch (i.getOrigen()) {
                        case PRUEBA_PILOTO -> "Falla en el servidor piloto";
                        case FALLO_EJECUCION -> "Falla de ejecución";
                        case TIEMPO_EXCEDIDO -> "Tiempo excedido";
                        case VALIDACION_TECNICA_NO_CONFORME -> "Validación técnica no conforme";
                        case RECHAZO_FUNCIONAL -> "Rechazo del responsable";
                    },
                    switch (i.getEstado()) {
                        case ABIERTA -> "Abierta";
                        case EN_ATENCION -> "En atención";
                        case CERRADA -> "Cerrada";
                    },
                    i.getUsuarioAtencion() == null ? null : i.getUsuarioAtencion().getNombreCompleto(),
                    unir(i.getError(), i.getDiagnostico(), i.getResultado())));
        }

        eventos.sort(Comparator.comparing(EventoBitacora::fecha, Comparator.nullsLast(Comparator.naturalOrder())));
        return eventos;
    }

    private static boolean delServidor(Integer id, Integer filtro) {
        return filtro == null || filtro.equals(id);
    }

    private static String mediciones(List<MedicionMetrica> mediciones) {
        return mediciones.stream()
                .map(m -> "%s%s=%s".formatted(m.getMetrica().getCodigo(),
                        m.getInstancia() == null || m.getInstancia().isEmpty() ? "" : "[" + m.getInstancia() + "]",
                        m.getMetrica().isEsLista() ? String.join("|", m.getElementos())
                                : m.getValor() == null ? "" : m.getValor().stripTrailingZeros().toPlainString()))
                .collect(Collectors.joining(", "));
    }

    private static String unir(String... partes) {
        String texto = Stream.of(partes).filter(p -> p != null && !p.isBlank()).collect(Collectors.joining(" · "));
        return texto.isEmpty() ? null : texto;
    }

    // ---------------------------------------------------------------- Resumen para la vista gráfica

    private List<MantenimientoBitacora> mantenimientos(List<Integer> candidatos, Integer idServidor, AlcanceUsuario alcance) {
        List<Integer> ids = candidatos.stream().limit(MAXIMO_MANTENIMIENTOS).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<OrdenDetalle> visibles = bitacora.participaciones(ids, idServidor, alcance.veTodoElInventario(), alcance.usuarioId());
        Map<Integer, List<OrdenDetalle>> porOrden = visibles.stream()
                .collect(Collectors.groupingBy(d -> d.getOrden().getId()));
        Map<Integer, Orden> porId = bitacora.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Orden::getId, Function.identity()));
        Map<Integer, EstadoValidacionFuncional> validaciones = bitacora
                .validacionesFuncionales(ids, alcance.veTodoElInventario(), alcance.usuarioId()).stream()
                .collect(Collectors.toMap(BitacoraRepositorio.ValidacionFuncional::getIdOrden,
                        BitacoraRepositorio.ValidacionFuncional::getEstado));
        DatosParticipacion datos = datosParticipacion(visibles.stream().map(OrdenDetalle::getId).toList());
        return ids.stream().map(id -> {
            Orden orden = porId.get(id);
            List<OrdenDetalle> detalles = porOrden.getOrDefault(id, List.of());
            if (orden == null || (!alcance.veTodoElInventario() && detalles.isEmpty())) {
                throw RecursoNoEncontradoException.de("la orden", id);
            }
            return mantenimiento(orden, detalles, datos, validaciones.get(id));
        }).toList();
    }

    private DatosParticipacion datosParticipacion(List<Integer> idsDetalle) {
        Map<Integer, List<TareaBitacora>> tareas = new HashMap<>();
        Map<Integer, Map<TipoEvaluacion, NivelAlerta>> niveles = new HashMap<>();
        Map<Integer, ResultadoValidacionTecnica> validaciones = new HashMap<>();
        Map<Integer, Integer> incidencias = new HashMap<>();
        for (int i = 0; i < idsDetalle.size(); i += PARTICIPACIONES_POR_LOTE) {
            List<Integer> lote = idsDetalle.subList(i, Math.min(i + PARTICIPACIONES_POR_LOTE, idsDetalle.size()));
            tareas.putAll(bitacora.tareas(lote).stream()
                    .collect(Collectors.groupingBy(BitacoraRepositorio.Tarea::getIdDetalle,
                            Collectors.mapping(ServicioBitacora::tarea, Collectors.toList()))));
            niveles.putAll(bitacora.niveles(lote, EstadoEvaluacion.FINALIZADA).stream()
                    .collect(Collectors.groupingBy(BitacoraRepositorio.NivelEvaluacion::getIdDetalle,
                            Collectors.toMap(BitacoraRepositorio.NivelEvaluacion::getTipo,
                                    n -> Objects.requireNonNullElse(n.getNivel(), NivelAlerta.NORMAL), NivelAlerta::peor))));
            bitacora.validacionesTecnicas(lote).forEach(v -> validaciones.put(v.getIdDetalle(), v.getResultado()));
            bitacora.incidencias(lote).forEach(c -> incidencias.put(c.getIdDetalle(), (int) c.getCantidad()));
        }
        return new DatosParticipacion(tareas, niveles, validaciones, incidencias);
    }

    private static TareaBitacora tarea(BitacoraRepositorio.Tarea tarea) {
        Instant inicio = tarea.getInicio();
        Instant fin = tarea.getFin();
        return new TareaBitacora(tarea.getNombre(), tarea.getEstado(),
                inicio == null || fin == null ? null : Duration.between(inicio, fin).toSeconds());
    }

    private static MantenimientoBitacora mantenimiento(Orden orden, List<OrdenDetalle> detalles, DatosParticipacion datos,
                                                       EstadoValidacionFuncional validacionFuncional) {
        EstadoOrden alCerrar = estadoAlCerrar(orden);
        List<ParticipacionServidor> participaciones = detalles.stream()
                .map(d -> participacion(d, orden.getEstado(), alCerrar, datos)).toList();

        Instant inicioReal = participaciones.stream().map(ParticipacionServidor::inicioReal).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        Instant finReal = participaciones.stream().map(ParticipacionServidor::finReal).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        Instant cierre = orden.getEstado() == EstadoOrden.CERRADA || orden.getEstado() == EstadoOrden.CANCELADA
                ? orden.getHistorial().stream().map(HistorialEstadoOrden::getFechaHora).max(Comparator.naturalOrder())
                .orElse(null)
                : null;
        boolean conIncidencias = participaciones.stream().anyMatch(p -> p.incidencias() > 0);
        boolean intervino = participaciones.stream().anyMatch(p -> intervino(p.tareas()));

        return new MantenimientoBitacora(orden.getId(), orden.getCodigo(), orden.esGrupal(),
                orden.esGrupal() ? orden.getGrupo().getNombre() : null, orden.getOrigen(), orden.prioridad(),
                orden.getEstado(), orden.getEstado().etapa(),
                resultado(orden.getEstado(), alCerrar, intervino, conIncidencias),
                orden.getFechaInicioProgramada(), orden.getFechaFinProgramada(), inicioReal, finReal, cierre,
                validacionFuncional, participaciones);
    }

    private static ParticipacionServidor participacion(OrdenDetalle d, EstadoOrden estado, EstadoOrden alCerrar,
                                                       DatosParticipacion datos) {
        Integer idDetalle = d.getId();
        List<TareaBitacora> tareas = datos.tareas().getOrDefault(idDetalle, List.of());
        Map<TipoEvaluacion, NivelAlerta> niveles = datos.niveles().getOrDefault(idDetalle, Map.of());
        int incidencias = datos.incidencias().getOrDefault(idDetalle, 0);
        return new ParticipacionServidor(d.getServidor().getId(), d.getServidor().getHostname(), d.getEstado(),
                d.isEsServidorPiloto(), niveles.get(TipoEvaluacion.PREVIA), niveles.get(TipoEvaluacion.POSTERIOR),
                datos.validaciones().get(idDetalle), incidencias,
                resultado(estado, alCerrar, intervino(tareas), incidencias > 0), d.getFechaRealInicio(),
                d.getFechaRealFin(), tareas);
    }

    private static boolean intervino(List<TareaBitacora> tareas) {
        return tareas.stream().anyMatch(t -> t.estado() == EstadoTareaMop.FINALIZADA || t.estado() == EstadoTareaMop.FALLIDA);
    }

    private record DatosParticipacion(Map<Integer, List<TareaBitacora>> tareas,
                                      Map<Integer, Map<TipoEvaluacion, NivelAlerta>> niveles,
                                      Map<Integer, ResultadoValidacionTecnica> validaciones,
                                      Map<Integer, Integer> incidencias) {
    }

    /**
     * Estado desde el que se cerró la orden. Todas terminan en CERRADA (tabla 6): si se canceló, se rechazó o se cerró
     * sin implementación, eso queda en el paso anterior al cierre. Nulo si la orden sigue abierta.
     */
    static EstadoOrden estadoAlCerrar(Orden orden) {
        if (orden.getEstado() != EstadoOrden.CERRADA) {
            return null;
        }
        return orden.getHistorial().stream()
                .filter(h -> h.getEstadoNuevo() == EstadoOrden.CERRADA)
                .map(HistorialEstadoOrden::getEstadoAnterior)
                .reduce((a, b) -> b)
                .orElse(null);
    }

    /**
     * Resultado de un vistazo. Abierta, el estado dice qué espera; cerrada, el estado desde el que se cerró dice si
     * se hizo, y las tareas y las incidencias, cómo
     *
     * @param alCerrar estado anterior al cierre; nulo si la orden sigue abierta
     */
    public static ResultadoMantenimiento resultado(EstadoOrden estado, EstadoOrden alCerrar, boolean intervino,
                                                   boolean conIncidencias) {
        boolean cerrada = estado == EstadoOrden.CERRADA;
        EstadoOrden decisivo = cerrada && alCerrar != null ? alCerrar : estado;
        if (decisivo == EstadoOrden.CANCELADA || cerrada && decisivo == EstadoOrden.SIN_IMPLEMENTACION) {
            return ResultadoMantenimiento.CANCELADO;
        }
        if (SIN_AUTORIZAR.contains(decisivo)) {
            return ResultadoMantenimiento.NO_AUTORIZADO;
        }
        if (cerrada) {
            if (conIncidencias) {
                return ResultadoMantenimiento.CON_INCIDENCIA;
            }
            return intervino ? ResultadoMantenimiento.EXITOSO : ResultadoMantenimiento.SIN_INTERVENCION;
        }
        if (A_ATENDER.contains(estado)) {
            return ResultadoMantenimiento.REQUIERE_ATENCION;
        }
        return PROGRAMADAS.contains(estado) ? ResultadoMantenimiento.PROGRAMADO : ResultadoMantenimiento.EN_CURSO;
    }

    // ---------------------------------------------------------------- Etiquetas

    private static String etiqueta(EstadoOrden e) {
        return switch (e) {
            case PROGRAMADA -> "Programada";
            case EN_EVALUACION -> "En evaluación";
            case SIN_IMPLEMENTACION -> "Sin implementación";
            case PENDIENTE_AUTORIZACION -> "Pendiente de autorización";
            case AUTORIZADA -> "Autorizada";
            case EN_COLA -> "En cola";
            case EN_EJECUCION -> "En ejecución";
            case PENDIENTE_VALIDACION -> "Pendiente de validación";
            case INCIDENCIA -> "Incidencia";
            case REPROGRAMADA -> "Reprogramada";
            case RECHAZADA -> "Rechazada";
            case AUTORIZACION_VENCIDA -> "Autorización vencida";
            case VALIDACION_VENCIDA -> "Validación vencida";
            case CANCELADA -> "Cancelada";
            case CERRADA -> "Cerrada";
        };
    }

    private static String etiqueta(TipoEvaluacion t) {
        return switch (t) {
            case REVISION_PERIODICA -> "Revisión periódica";
            case PREVIA -> "Evaluación previa";
            case POSTERIOR -> "Validación técnica (análisis)";
        };
    }

    private static String etiqueta(EstadoEvaluacion e) {
        return switch (e) {
            case EN_CURSO -> "En curso";
            case FINALIZADA -> "Finalizada";
            case FALLIDA -> "Fallida";
        };
    }

    private static String etiqueta(NivelAlerta n) {
        return switch (n) {
            case NORMAL -> "Normal";
            case ADVERTENCIA -> "Advertencia";
            case CRITICO -> "Crítico";
        };
    }

    private static String etiqueta(EstadoAutorizacion e) {
        return switch (e) {
            case PENDIENTE -> "Pendiente";
            case APROBADA -> "Aprobada";
            case RECHAZADA -> "Rechazada";
            case FECHA_PROPUESTA -> "Otra fecha propuesta";
            case VENCIDA -> "Vencida";
        };
    }

    private static String etiqueta(EstadoTareaMop e) {
        return switch (e) {
            case PENDIENTE -> "Pendiente";
            case EN_EJECUCION -> "En ejecución";
            case FINALIZADA -> "Finalizada";
            case FALLIDA -> "Fallida";
            case NO_INICIADA -> "No iniciada";
        };
    }

    private static String etiqueta(EstadoValidacionFuncional e) {
        return switch (e) {
            case PENDIENTE -> "Pendiente";
            case CONFORME -> "Conforme";
            case RECHAZADA -> "No conforme";
            case VENCIDA -> "Vencida";
        };
    }

    // ---------------------------------------------------------------- Resultados

    /** Una fila de la bitácora exportable */
    public record EventoBitacora(Instant fecha, String orden, String evento, String servidor, String descripcion,
                                 String resultado, String usuario, String detalle) {
    }

    /** @param objetivo código de la orden, hostname del servidor o nombre del grupo, para nombrar el archivo */
    public record EventosBitacora(String objetivo, List<EventoBitacora> eventos) {
    }

    /**
     * @param objetivo hostname del servidor o nombre del grupo
     * @param truncada hay más mantenimientos que los {@value #MAXIMO_MANTENIMIENTOS} más recientes que se muestran
     */
    public record Bitacora(String objetivo, List<MantenimientoBitacora> mantenimientos, boolean truncada) {
    }

    /**
     * Un ciclo de mantenimiento resumido
     *
     * @param inicioReal el primer servidor que inició; nulo si ninguno se ejecutó
     * @param fechaCierre cuándo se cerró o canceló la orden
     */
    public record MantenimientoBitacora(Integer idOrden, String codigo, boolean grupal, String grupo, OrigenOrden origen,
                                        PrioridadOrden prioridad, EstadoOrden estado, EtapaOrden etapa,
                                        ResultadoMantenimiento resultado, Instant inicioProgramado,
                                        Instant finProgramado, Instant inicioReal, Instant finReal,
                                        Instant fechaCierre, EstadoValidacionFuncional validacionFuncional,
                                        List<ParticipacionServidor> servidores) {
    }

    /**
     * @param nivelAntes  nivel del análisis previo; nulo si no hubo uno concluyente
     * @param nivelDespues nivel de la validación técnica
     */
    public record ParticipacionServidor(Integer idServidor, String servidor, EstadoDetalleOrden estado, boolean piloto,
                                        NivelAlerta nivelAntes, NivelAlerta nivelDespues,
                                        ResultadoValidacionTecnica validacionTecnica, int incidencias,
                                        ResultadoMantenimiento resultado, Instant inicioReal, Instant finReal,
                                        List<TareaBitacora> tareas) {
    }

    public record TareaBitacora(String nombre, EstadoTareaMop estado, Long duracionSegundos) {
    }

    public enum ResultadoMantenimiento {
        PROGRAMADO,
        EN_CURSO,
        EXITOSO,
        SIN_INTERVENCION,
        CON_INCIDENCIA,
        REQUIERE_ATENCION,
        NO_AUTORIZADO,
        CANCELADO
    }

}
