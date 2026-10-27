package com.komainos.mantenimiento.mapper;

import com.komainos.inventario.model.NivelAlerta;
import com.komainos.mantenimiento.dto.FichaAutomatizacionRespuesta;
import com.komainos.mantenimiento.dto.FichaAutomatizacionRespuesta.AnalisisRespuesta;
import com.komainos.mantenimiento.dto.FichaAutomatizacionRespuesta.FilaComparativa;
import com.komainos.mantenimiento.dto.FichaAutomatizacionRespuesta.MedicionRespuesta;
import com.komainos.mantenimiento.dto.FichaAutomatizacionRespuesta.PlanMop;
import com.komainos.mantenimiento.dto.FichaAutomatizacionRespuesta.ReporteServidor;
import com.komainos.mantenimiento.dto.FichaAutomatizacionRespuesta.TareaMopRespuesta;
import com.komainos.mantenimiento.dto.IncidenciaRespuesta;
import com.komainos.mantenimiento.dto.OperacionRespuestas;
import com.komainos.mantenimiento.dto.OrdenDetalleRespuesta;
import com.komainos.mantenimiento.dto.PoliticaCambioRespuesta;
import com.komainos.mantenimiento.model.EstadoEvaluacion;
import com.komainos.mantenimiento.model.EstadoTareaMop;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.EvaluacionUmbral;
import com.komainos.mantenimiento.model.Incidencia;
import com.komainos.mantenimiento.model.MedicionMetrica;
import com.komainos.mantenimiento.model.Mop;
import com.komainos.mantenimiento.model.MopTarea;
import com.komainos.mantenimiento.model.NotificacionRegla;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.ReglaPoliticaCambio;
import com.komainos.mantenimiento.model.SolicitudAutorizacion;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import com.komainos.mantenimiento.service.ServicioConsultaAutomatizacion.FichaAutomatizacion;
import com.komainos.shared.dto.ReferenciaSimple;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Espera las asociaciones ya cargadas por el servicio: open-in-view está desactivado */
public final class AutomatizacionMapeador {

    private AutomatizacionMapeador() {
    }

    public static FichaAutomatizacionRespuesta ficha(FichaAutomatizacion f) {
        var a = f.acciones();
        List<AnalisisRespuesta> analisis = f.evaluaciones().stream().map(e -> analisis(e, f.cuentas())).toList();
        return new FichaAutomatizacionRespuesta(
                f.orden().getId(),
                f.simulado(),
                OrdenMapeador.resumen(f.orden()),
                f.siguientePaso(),
                new FichaAutomatizacionRespuesta.AccionesOrden(a.evaluar(), a.decidir(), a.proponerFecha(),
                        a.decidirPropuesta(), a.validar(), a.cerrarSinImplementacion(), a.procesarCola()),
                f.mops().stream().map(m -> plan(m, f.maxDuracionMop(), f.cuentas())).toList(),
                analisis,
                f.autorizaciones().stream().map(AutomatizacionMapeador::ronda).toList(),
                f.validacion().map(v -> new FichaAutomatizacionRespuesta.ValidacionFuncionalRespuesta(v.getId(),
                        v.getResponsable().getId(), v.getResponsable().getNombreCompleto(), v.getEstado(),
                        v.getFechaSolicitud(), v.getFechaLimite(), v.getFechaRespuesta(), v.getObservaciones()))
                        .orElse(null),
                f.validacionesTecnicas().stream()
                        .map(v -> new FichaAutomatizacionRespuesta.ValidacionTecnicaRespuesta(v.getDetalle().getId(),
                                v.getDetalle().getServidor().getHostname(), v.getResultado(), v.getObservaciones(),
                                v.getFechaValidacion()))
                        .toList(),
                f.incidencias().stream().map(AutomatizacionMapeador::incidencia).toList(),
                reporte(f),
                f.orden().getHistorial().stream()
                        .map(h -> new OrdenDetalleRespuesta.CambioEstado(h.getEstadoAnterior(), h.getEstadoNuevo(),
                                h.getMotivo(), h.getUsuario() == null ? null
                                : new ReferenciaSimple(h.getUsuario().getId(), h.getUsuario().getNombreCompleto()),
                                h.getFechaHora()))
                        .toList());
    }

    public static AnalisisRespuesta analisis(Evaluacion e, Map<Integer, String> cuentas) {
        return new AnalisisRespuesta(e.getId(), e.getDetalle() == null ? null : e.getDetalle().getId(), e.getServidor().getId(),
                e.getServidor().getHostname(), e.getTipo(), e.getEstado(), e.estaFinalizada() ? e.nivel() : null,
                e.getVersion().getImplementacion().getScript().getNombre(), e.getVersion().getId(),
                e.getVersion().getNumeroVersion(), e.getIdCuentaServicio(), cuentas.get(e.getIdCuentaServicio()),
                e.getFechaInicio(), e.getFechaFin(), e.getError(),
                e.getMediciones().stream().map(AutomatizacionMapeador::medicion).toList());
    }

    public static IncidenciaRespuesta incidencia(Incidencia i) {
        OrdenDetalle d = i.getDetalle();
        return new IncidenciaRespuesta(i.getId(), d.getOrden().getId(), d.getOrden().getCodigo(), d.getServidor().getId(),
                d.getServidor().getHostname(), i.getOrigen(), i.getEstado(), i.getError(), i.getDiagnostico(),
                i.getAccionesRealizadas(), i.getResultado(),
                i.getUsuarioAtencion() == null ? null : i.getUsuarioAtencion().getNombreCompleto(),
                i.getFechaApertura(), i.getFechaCierre());
    }

    public static PoliticaCambioRespuesta politica(ReglaPoliticaCambio r, List<NotificacionRegla> notificaciones) {
        return new PoliticaCambioRespuesta(r.getId(), r.getEntorno().getId(), r.getEntorno().getNombre(),
                r.getAfectacion(), r.isRequiereResponsable(), r.isRequiereAdministrador(), r.isActivo(),
                r.getFechaActualizacion(), notificaciones.stream().map(NotificacionRegla::getDestinatario).toList());
    }

    public static OperacionRespuestas.ColaRespuesta cola(OrdenDetalle d, int posicion) {
        return new OperacionRespuestas.ColaRespuesta(d.getId(), d.getOrden().getId(), d.getOrden().getCodigo(),
                d.getServidor().getId(), d.getServidor().getHostname(), d.getOrden().prioridad(), d.getEstado(),
                posicion, d.isEsServidorPiloto(), d.getFechaPrevistaInicio(), d.getFechaPrevistaFin(),
                d.getFechaRealInicio());
    }

    public static OperacionRespuestas.UsoVersionRespuesta uso(MopTarea t) {
        OrdenDetalle d = t.getMop().getDetalle();
        return new OperacionRespuestas.UsoVersionRespuesta(t.getId(), d.getOrden().getId(), d.getOrden().getCodigo(),
                d.getServidor().getHostname(), t.getTarea().getNombre(), t.getEstado(), t.getFechaInicio(),
                t.getFechaFin());
    }

    public static OperacionRespuestas.EjecucionRespuesta ejecucion(MopTarea t, Function<Integer, String> cuentas) {
        OrdenDetalle d = t.getMop().getDetalle();
        var v = t.getVersion();
        return new OperacionRespuestas.EjecucionRespuesta(t.getId(), d.getOrden().getId(), d.getOrden().getCodigo(),
                d.getServidor().getId(), d.getServidor().getHostname(), t.getTarea().getNombre(),
                v == null ? null : v.getImplementacion().getScript().getId(),
                v == null ? null : v.getImplementacion().getScript().getNombre(),
                v == null ? null : v.getId(), v == null ? null : v.getNumeroVersion(),
                v == null ? null : v.getImplementacion().getVersionSistemaOperativo().descripcionCompleta(),
                t.getIdCuentaServicio() == null ? null : cuentas.apply(t.getIdCuentaServicio()),
                t.getEstado(), t.getCodigoRetorno(), t.getFechaInicio(), t.getFechaFin(),
                duracion(t.getFechaInicio(), t.getFechaFin()), t.getError());
    }

    private static PlanMop plan(Mop m, int maximo, Map<Integer, String> cuentas) {
        OrdenDetalle d = m.getDetalle();
        return new PlanMop(m.getId(), d.getId(), d.getServidor().getId(), d.getServidor().getHostname(), d.getEstado(),
                d.isEsServidorPiloto(), m.duracionTotal(), maximo, m.duracionTotal() > maximo, m.afectacion(),
                d.getFechaRealInicio(), d.getFechaRealFin(),
                m.getTareas().stream().map(t -> tarea(t, cuentas)).toList());
    }

    private static TareaMopRespuesta tarea(MopTarea t, Map<Integer, String> cuentas) {
        var v = t.getVersion();
        return new TareaMopRespuesta(t.getId(), t.getOrdenEjecucion(), t.getTarea().getId(), t.getTarea().getNombre(),
                v == null ? null : v.getImplementacion().getScript().getNombre(), v == null ? null : v.getId(),
                v == null ? null : v.getNumeroVersion(), t.getPrioridadAplicada(), t.getDuracionEstimadaMinutos(),
                t.getNivelAfectacion(), t.getIdCuentaServicio(), cuentas.get(t.getIdCuentaServicio()), t.getEstado(),
                t.getCodigoRetorno(), t.getSalida(), t.getError(), t.getFechaInicio(), t.getFechaFin(),
                duracion(t.getFechaInicio(), t.getFechaFin()));
    }

    private static FichaAutomatizacionRespuesta.RondaAutorizacion ronda(SolicitudAutorizacion s) {
        return new FichaAutomatizacionRespuesta.RondaAutorizacion(s.getId(), s.getNumeroRonda(), s.getFechaLimite(),
                s.getRegla().getEntorno().getNombre(), s.getRegla().getAfectacion(), s.esAutomatica(),
                s.getDecisiones().stream()
                        .map(d -> new FichaAutomatizacionRespuesta.DecisionAutorizacion(d.getId(), d.getTipo(), d.getOrdenSecuencia(),
                                d.getEstado(), d.getUsuarioDecision() == null ? null : d.getUsuarioDecision().getNombreCompleto(),
                                d.getFechaSolicitud(), d.getFechaDecision(), d.getFechaPropuesta(), d.getMotivo()))
                        .toList());
    }

    private static MedicionRespuesta medicion(MedicionMetrica m) {
        return new MedicionRespuesta(m.getMetrica().getCodigo(), m.getMetrica().getNombre(), m.getMetrica().getUnidad(),
                m.getInstancia(), m.getValor(), m.getMetrica().isEsLista() ? List.copyOf(m.getElementos()) : null,
                nivel(m),
                m.getUmbrales().stream()
                        .map(u -> new FichaAutomatizacionRespuesta.UmbralEvaluadoRespuesta(u.getIdUmbral(), u.getIdExcepcion(),
                                u.getNivel(), u.getOperador() == null ? null : u.getOperador().simbolo(),
                                u.getAdvertencia(), u.getCritico(), u.getIdLista(), u.getNivelFaltante(),
                                u.getNivelSobrante()))
                        .toList());
    }

    /** RF54: por servidor, la última evaluación previa frente a la última posterior y el resultado de la MOP */
    private static List<ReporteServidor> reporte(FichaAutomatizacion f) {
        Map<Integer, List<Evaluacion>> porDetalle = f.evaluaciones().stream()
                .filter(e -> e.getDetalle() != null)
                .collect(Collectors.groupingBy(e -> e.getDetalle().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<Integer, Mop> mops = f.mops().stream()
                .collect(Collectors.toMap(m -> m.getDetalle().getId(), Function.identity(), (a, b) -> a, LinkedHashMap::new));
        List<Integer> detalles = new ArrayList<>(porDetalle.keySet());
        mops.keySet().stream().filter(id -> !detalles.contains(id)).forEach(detalles::add);

        List<ReporteServidor> reporte = new ArrayList<>();
        for (Integer idDetalle : detalles) {
            List<Evaluacion> analisis = porDetalle.getOrDefault(idDetalle, List.of());
            Optional<Evaluacion> previa = ultima(analisis, TipoEvaluacion.PREVIA);
            Optional<Evaluacion> posterior = ultima(analisis, TipoEvaluacion.POSTERIOR);
            Mop mop = mops.get(idDetalle);
            // El servidor de la evaluación viene cargado; su detalle puede ser un proxy sin inicializar
            var servidor = mop != null ? mop.getDetalle().getServidor() : analisis.getFirst().getServidor();

            Map<String, FilaComparativa> filas = new LinkedHashMap<>();
            previa.filter(Evaluacion::estaFinalizada).ifPresent(e -> e.getMediciones().forEach(m ->
                    filas.put(clave(m), new FilaComparativa(m.getMetrica().getCodigo(), m.getMetrica().getNombre(),
                            m.getMetrica().getUnidad(), m.getInstancia(), texto(m), null, nivel(m), null))));
            posterior.filter(Evaluacion::estaFinalizada).ifPresent(e -> e.getMediciones().forEach(m -> {
                FilaComparativa antes = filas.get(clave(m));
                filas.put(clave(m), new FilaComparativa(m.getMetrica().getCodigo(), m.getMetrica().getNombre(),
                        m.getMetrica().getUnidad(), m.getInstancia(), antes == null ? null : antes.antes(), texto(m),
                        antes == null ? null : antes.nivelAntes(), nivel(m)));
            }));

            List<String> errores = new ArrayList<>();
            List<String> advertencias = new ArrayList<>();
            int finalizadas = 0;
            int fallidas = 0;
            int noIniciadas = 0;
            if (mop != null) {
                for (MopTarea t : mop.getTareas()) {
                    if (t.getEstado() == EstadoTareaMop.FINALIZADA) {
                        finalizadas++;
                    } else if (t.getEstado() == EstadoTareaMop.FALLIDA) {
                        fallidas++;
                        errores.add("%s: %s".formatted(t.getTarea().getNombre(), t.getError()));
                    } else if (t.getEstado() == EstadoTareaMop.NO_INICIADA) {
                        noIniciadas++;
                    }
                }
                if (mop.duracionTotal() > f.maxDuracionMop()) {
                    advertencias.add("La MOP estima %d min y supera el máximo de %d min por MOP"
                            .formatted(mop.duracionTotal(), f.maxDuracionMop()));
                }
                if (noIniciadas > 0) {
                    advertencias.add("%d tarea(s) no se iniciaron".formatted(noIniciadas));
                }
            }
            // Un análisis en curso no es un error: solo se informan los que fallaron
            previa.filter(e -> e.getEstado() == EstadoEvaluacion.FALLIDA)
                    .ifPresent(e -> errores.add("Análisis previo: " + e.getError()));
            posterior.filter(e -> e.getEstado() == EstadoEvaluacion.FALLIDA)
                    .ifPresent(e -> errores.add("Análisis posterior: " + e.getError()));
            filas.values().stream()
                    .filter(fila -> fila.nivelDespues() != null && fila.nivelDespues() != NivelAlerta.NORMAL)
                    .forEach(fila -> advertencias.add("%s sigue en %s después del mantenimiento".formatted(
                            fila.nombre(), fila.nivelDespues() == NivelAlerta.CRITICO ? "crítico" : "advertencia")));
            reporte.add(new ReporteServidor(servidor.getId(), servidor.getHostname(),
                    List.copyOf(filas.values()), finalizadas, fallidas, noIniciadas, errores, advertencias));
        }
        return reporte;
    }

    private static Optional<Evaluacion> ultima(List<Evaluacion> evaluaciones, TipoEvaluacion tipo) {
        return evaluaciones.stream().filter(e -> e.getTipo() == tipo).reduce((a, b) -> b);
    }

    private static NivelAlerta nivel(MedicionMetrica m) {
        return m.getUmbrales().stream().map(EvaluacionUmbral::getNivel).reduce(NivelAlerta.NORMAL, NivelAlerta::peor);
    }

    private static String clave(MedicionMetrica m) {
        return m.getMetrica().getCodigo() + "\u0000" + m.getInstancia();
    }

    private static String texto(MedicionMetrica m) {
        if (m.getMetrica().isEsLista()) {
            return String.join(", ", m.getElementos());
        }
        return m.getValor() == null ? null : m.getValor().stripTrailingZeros().toPlainString();
    }

    private static Long duracion(Instant inicio, Instant fin) {
        return inicio == null || fin == null ? null : Duration.between(inicio, fin).toSeconds();
    }
}
