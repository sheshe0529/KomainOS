package com.komainos.mantenimiento.dto;

import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.inventario.model.NivelAlerta;
import com.komainos.mantenimiento.model.EstadoAutorizacion;
import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoEvaluacion;
import com.komainos.mantenimiento.model.EstadoTareaMop;
import com.komainos.mantenimiento.model.EstadoValidacionFuncional;
import com.komainos.mantenimiento.model.ResultadoValidacionTecnica;
import com.komainos.mantenimiento.model.TipoAprobador;
import com.komainos.mantenimiento.model.TipoEvaluacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Bitácora de mantenimiento de una orden (RF53) y reporte comparativo (RF54): MOP con el resultado de cada tarea,
 * análisis antes y después, rondas de autorización, validaciones e incidencias, sin secretos (RNF11). El responsable solo
 * recibe lo de sus servidores.
 *
 * @param siguientePaso qué espera la orden para avanzar, en una frase
 * @param acciones      lo que el usuario autenticado puede hacer ahora
 */
public record FichaAutomatizacionRespuesta(Integer idOrden, boolean simulado, OrdenResumenRespuesta orden,
                                           String siguientePaso, AccionesOrden acciones, List<PlanMop> mops,
                                           List<AnalisisRespuesta> evaluaciones, List<RondaAutorizacion> autorizaciones,
                                           ValidacionFuncionalRespuesta validacionFuncional,
                                           List<ValidacionTecnicaRespuesta> validacionesTecnicas,
                                           List<IncidenciaRespuesta> incidencias, List<ReporteServidor> reporte,
                                           List<OrdenDetalleRespuesta.CambioEstado> historial) {

    public record AccionesOrden(boolean evaluar, boolean decidir, boolean proponerFecha, boolean decidirPropuesta,
                                boolean validar, boolean cerrarSinImplementacion, boolean procesarCola) {
    }

    /** MOP de un servidor (RF41) con la duración estimada frente al máximo por MOP */
    public record PlanMop(Integer id, Integer idDetalle, Integer idServidor, String servidor,
                          EstadoDetalleOrden estadoServidor, boolean piloto, int duracionMinutos, int maximoMinutos,
                          boolean superaDuracionMaxima, NivelAfectacion afectacion, Instant inicioReal, Instant finReal,
                          List<TareaMopRespuesta> tareas) {
    }

    /** Lo fijado al generar la MOP (RF42) y el resultado de su ejecución (RF53) */
    public record TareaMopRespuesta(Integer id, Integer orden, Integer idTarea, String tarea, String script,
                                    Integer idVersion, Integer numeroVersion, int prioridad, int duracionMinutos,
                                    NivelAfectacion afectacion, Integer idCuentaServicio, String cuenta,
                                    EstadoTareaMop estado, Integer codigoRetorno, String salida, String error,
                                    Instant fechaInicio, Instant fechaFin, Long duracionSegundos) {
    }

    public record AnalisisRespuesta(Integer id, Integer idDetalle, Integer idServidor, String servidor,
                                    TipoEvaluacion tipo, EstadoEvaluacion estado, NivelAlerta nivel, String script,
                                    Integer idVersion, Integer numeroVersion, Integer idCuentaServicio, String cuenta,
                                    Instant fechaInicio, Instant fechaFin, String error,
                                    List<MedicionRespuesta> mediciones) {
    }

    public record MedicionRespuesta(String codigo, String nombre, String unidad, String instancia, BigDecimal valor,
                                    List<String> elementos, NivelAlerta nivel, List<UmbralEvaluadoRespuesta> umbrales) {
    }

    /** Los valores que rigieron al evaluar, aunque el umbral haya cambiado después (DEC-41) */
    public record UmbralEvaluadoRespuesta(Integer idUmbral, Integer idExcepcion, NivelAlerta nivel, String operador,
                                          BigDecimal advertencia, BigDecimal critico, Integer idLista,
                                          NivelAlerta nivelFaltante, NivelAlerta nivelSobrante) {
    }

    /** Ronda de autorización con su plazo compartido (RF75); sin decisiones es automática */
    public record RondaAutorizacion(Integer id, int ronda, Instant fechaLimite, String entorno,
                                    NivelAfectacion afectacion, boolean automatica,
                                    List<DecisionAutorizacion> decisiones) {
    }

    public record DecisionAutorizacion(Integer id, TipoAprobador tipo, int secuencia, EstadoAutorizacion estado,
                                       String usuario, Instant fechaSolicitud, Instant fechaDecision,
                                       Instant fechaPropuesta, String motivo) {
    }

    public record ValidacionFuncionalRespuesta(Integer id, Integer idResponsable, String responsable,
                                               EstadoValidacionFuncional estado, Instant fechaSolicitud,
                                               Instant fechaLimite, Instant fechaRespuesta, String observaciones) {
    }

    public record ValidacionTecnicaRespuesta(Integer idDetalle, String servidor, ResultadoValidacionTecnica resultado,
                                             String observaciones, Instant fecha) {
    }

    /**
     * Reporte comparativo de un servidor (RF54): métricas antes y después, tareas ejecutadas y no iniciadas, errores y
     * advertencias.
     */
    public record ReporteServidor(Integer idServidor, String servidor, List<FilaComparativa> metricas,
                                  int tareasFinalizadas, int tareasFallidas, int tareasNoIniciadas,
                                  List<String> errores, List<String> advertencias) {
    }

    /** @param antes y después como texto: un número o los elementos de una lista separados por coma */
    public record FilaComparativa(String codigo, String nombre, String unidad, String instancia, String antes,
                                  String despues, NivelAlerta nivelAntes, NivelAlerta nivelDespues) {
    }
}
