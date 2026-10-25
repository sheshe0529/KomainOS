package com.komainos.catalogo.dto;
import com.komainos.catalogo.model.*;import java.util.Set;
public record TareaRespuesta(Integer id,Integer idScript,String script,String nombre,String descripcion,NivelAfectacion nivelAfectacion,int prioridad,int duracionEstimadaMinutos,boolean activo,Set<Integer> idsUmbrales){
 public static TareaRespuesta de(Tarea t){return new TareaRespuesta(t.getId(),t.getScript().getId(),t.getScript().getNombre(),t.getNombre(),t.getDescripcion(),t.getNivelAfectacion(),t.getPrioridad(),t.getDuracionEstimadaMinutos(),t.isActivo(),Set.copyOf(t.getIdsUmbrales()));}
}
