package com.komainos.catalogo.dto;

import com.komainos.catalogo.model.NivelAfectacion;

import java.util.List;
import java.util.Set;

/**
 * @param sistemasCompatibles versiones de sistema operativo con una versión habilitada del script (RF23)
 */
public record TareaRespuesta(Integer id, Integer idScript, String script, boolean scriptActivo, String nombre,
                             String descripcion, NivelAfectacion nivelAfectacion, int prioridad,
                             int duracionEstimadaMinutos, boolean activo, Set<Integer> idsUmbrales,
                             List<String> sistemasCompatibles) {
}
