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
import com.komainos.mantenimiento.model.ValidacionFuncional;
import com.komainos.mantenimiento.model.ValidacionTecnica;
import com.komainos.mantenimiento.repository.OrdenRepositorio;
import com.komainos.mantenimiento.service.ServicioConsultaAutomatizacion.FichaAutomatizacion;
import com.komainos.seguridad.model.AlcanceUsuario;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Bitácora de mantenimiento (RF53) de una orden, de un servidor o de un grupo: los eventos de cada ciclo en orden
 * cronológico, para exportarlos, y un resumen por mantenimiento para la vista gráfica. Se arma con la misma ficha que
 * ve el panel, así el alcance del responsable y los datos coinciden.
 */
@Service
@RequiredArgsConstructor
public class ServicioBitacora {

    /** Tope por consulta: la bitácora de un servidor con mantenimiento mensual cubre ocho años */
    public static final int MAXIMO_MANTENIMIENTOS = 100;

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
        List<FichaAutomatizacion> fichas = fichas(ids, alcance);
        return new Bitacora(servidor.getHostname(), fichas.stream().map(f -> mantenimiento(f, idServidor, alcance)).toList(),
                ids.size() > MAXIMO_MANTENIMIENTOS);
    }

    @Transactional(readOnly = true)
    public EventosBitacora eventosDelServidor(Integer idServidor, AlcanceUsuario alcance) {
        Servidor servidor = servidores.obtener(idServidor, alcance);
        List<Integer> ids = ordenes.findIdsDelServidor(idServidor, PageRequest.of(0, MAXIMO_MANTENIMIENTOS));
        return new EventosBitacora(servidor.getHostname(), cronologia(fichas(ids, alcance), idServidor));
    }

    /** Los mantenimientos del grupo como tal: cada orden grupal con el resultado de cada integrante */
    @Transactional(readOnly = true)
    public Bitacora delGrupo(Integer idGrupo, AlcanceUsuario alcance) {
        String nombre = grupos.ficha(idGrupo, alcance).grupo().getNombre();
        List<Integer> ids = ordenes.findIdsDelGrupo(idGrupo, PageRequest.of(0, MAXIMO_MANTENIMIENTOS + 1));
        List<FichaAutomatizacion> fichas = fichas(ids, alcance);
        return new Bitacora(nombre, fichas.stream().map(f -> mantenimiento(f, null, alcance)).toList(),
                ids.size() > MAXIMO_MANTENIMIENTOS);
    }

    /** @param idServidor solo los eventos de ese integrante y los de la orden; nulo para todo el grupo */
    @Transactional(readOnly = true)
    public EventosBitacora eventosDelGrupo(Integer idGrupo, Integer idServidor, AlcanceUsuario alcance) {
        String nombre = grupos.ficha(idGrupo, alcance).grupo().getNombre();
        if (idServidor != null) {
            nombre += " " + servidores.obtener(idServidor, alcance).getHostname();
        }
        List<Integer> ids = ordenes.findIdsDelGrupo(idGrupo, PageRequest.of(0, MAXIMO_MANTENIMIENTOS));
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

    /**
     * @param idServidor resume solo la participación de ese servidor; nulo para todos los integrantes que el usuario
     *                   puede ver
     */
    static MantenimientoBitacora mantenimiento(FichaAutomatizacion f, Integer idServidor, AlcanceUsuario alcance) {
        Orden orden = f.orden();
        Map<Integer, Mop> mops = f.mops().stream()
                .collect(Collectors.toMap(m -> m.getDetalle().getId(), m -> m, (a, b) -> a));
        List<ParticipacionServidor> participaciones = orden.getDetalles().stream()
                .filter(d -> delServidor(d.getServidor().getId(), idServidor))
                .filter(d -> alcance.veTodoElInventario() || d.getServidor().esVisiblePara(alcance.usuarioId()))
                .sorted(Comparator.comparing(OrdenDetalle::getPosicionEjecucion))
                .map(d -> participacion(f, d, mops.get(d.getId())))
                .toList();

        Instant inicioReal = participaciones.stream().map(ParticipacionServidor::inicioReal).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        Instant finReal = participaciones.stream().map(ParticipacionServidor::finReal).filter(Objects::nonNull)
                .max(Comparator.naturalOrder()).orElse(null);
        Instant cierre = orden.getEstado() == EstadoOrden.CERRADA || orden.getEstado() == EstadoOrden.CANCELADA
                ? orden.getHistorial().stream().map(HistorialEstadoOrden::getFechaHora).max(Comparator.naturalOrder())
                .orElse(null)
                : null;
        boolean conIncidencias = participaciones.stream().anyMatch(p -> p.incidencias() > 0);
        boolean intervino = participaciones.stream().anyMatch(p -> p.tareas().stream()
                .anyMatch(t -> t.estado() == EstadoTareaMop.FINALIZADA || t.estado() == EstadoTareaMop.FALLIDA));

        return new MantenimientoBitacora(orden.getId(), orden.getCodigo(), orden.esGrupal(),
                orden.esGrupal() ? orden.getGrupo().getNombre() : null, orden.getOrigen(), orden.prioridad(),
                orden.getEstado(), orden.getEstado().etapa(),
                resultado(orden.getEstado(), estadoAlCerrar(orden), intervino, conIncidencias),
                orden.getFechaInicioProgramada(), orden.getFechaFinProgramada(), inicioReal, finReal, cierre,
                f.validacion().map(ValidacionFuncional::getEstado).orElse(null), participaciones);
    }

    private static ParticipacionServidor participacion(FichaAutomatizacion f, OrdenDetalle d, Mop mop) {
        Integer idDetalle = d.getId();
        List<Evaluacion> analisis = f.evaluaciones().stream()
                .filter(e -> e.getDetalle() != null && e.getDetalle().getId().equals(idDetalle))
                .toList();
        List<TareaBitacora> tareas = mop == null ? List.of() : mop.getTareas().stream()
                .map(t -> new TareaBitacora(t.getTarea().getNombre(), t.getEstado(),
                        t.getFechaInicio() == null || t.getFechaFin() == null ? null
                                : Duration.between(t.getFechaInicio(), t.getFechaFin()).toSeconds()))
                .toList();
        ResultadoValidacionTecnica validacion = f.validacionesTecnicas().stream()
                .filter(v -> v.getDetalle().getId().equals(idDetalle))
                .map(ValidacionTecnica::getResultado)
                .reduce((a, b) -> b).orElse(null);
        int incidencias = (int) f.incidencias().stream().filter(i -> i.getDetalle().getId().equals(idDetalle)).count();
        boolean intervino = tareas.stream()
                .anyMatch(t -> t.estado() == EstadoTareaMop.FINALIZADA || t.estado() == EstadoTareaMop.FALLIDA);
        return new ParticipacionServidor(d.getServidor().getId(), d.getServidor().getHostname(), d.getEstado(),
                d.isEsServidorPiloto(), nivel(analisis, TipoEvaluacion.PREVIA), nivel(analisis, TipoEvaluacion.POSTERIOR),
                validacion, incidencias,
                resultado(f.orden().getEstado(), estadoAlCerrar(f.orden()), intervino, incidencias > 0), d.getFechaRealInicio(),
                d.getFechaRealFin(), tareas);
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

    /** Nivel de la última evaluación de ese tipo que terminó; nulo si no hubo o no fue concluyente */
    private static NivelAlerta nivel(List<Evaluacion> analisis, TipoEvaluacion tipo) {
        return analisis.stream()
                .filter(e -> e.getTipo() == tipo && e.getEstado() == EstadoEvaluacion.FINALIZADA)
                .reduce((a, b) -> b)
                .map(Evaluacion::nivel)
                .orElse(null);
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
