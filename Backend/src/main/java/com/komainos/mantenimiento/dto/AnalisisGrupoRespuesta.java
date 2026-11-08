package com.komainos.mantenimiento.dto;

import com.komainos.mantenimiento.dto.FichaAutomatizacionRespuesta.AnalisisRespuesta;
import com.komainos.mantenimiento.model.TipoEvaluacion;

import java.time.Instant;
import java.util.List;

/**
 * Un evento de análisis de un grupo con el análisis de cada integrante (RF37, RF38, RF55)
 *
 * @param idOrden y orden: la orden de la evaluación previa o de la validación técnica; nulos en una revisión periódica
 * @param fecha   cuándo empezó el primero de los análisis
 */
public record AnalisisGrupoRespuesta(TipoEvaluacion tipo, Integer idOrden, String orden, Instant fecha,
                                     List<AnalisisRespuesta> analisis) {
}
