package com.komainos.mantenimiento.repository;

import com.komainos.ejecucion.model.TipoFallo;
import com.komainos.mantenimiento.model.ActividadEjecucion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Cada evento tiene un solo origen (DEC-60): la ficha de la orden consulta en bloque los de cada tipo de origen */
public interface ActividadEjecucionRepositorio extends JpaRepository<ActividadEjecucion, Integer> {

    /** Los pasos generales de la ejecución en esos servidores: inicio, comprobación de acceso y cierre */
    List<ActividadEjecucion> findByIdDetalleInOrderByFechaHoraAscIdAsc(Collection<Integer> idsDetalle);

    List<ActividadEjecucion> findByIdEvaluacionInOrderByFechaHoraAscIdAsc(Collection<Integer> idsEvaluacion);

    List<ActividadEjecucion> findByIdTareaInOrderByFechaHoraAscIdAsc(Collection<Integer> idsTarea);

    List<ActividadEjecucion> findByIdDetalleOrderByFechaHoraAscIdAsc(Integer idDetalle);

    List<ActividadEjecucion> findByIdTareaOrderByFechaHoraAscIdAsc(Integer idTarea);

    @Query("""
            select a.idEvaluacion as id, a.tipoFallo as tipoFallo from ActividadEjecucion a
            where a.idEvaluacion in :ids and a.tipoFallo is not null
            """)
    List<FalloRegistrado> fallosDeEvaluaciones(@Param("ids") Collection<Integer> ids);

    @Query("""
            select a.idTarea as id, a.tipoFallo as tipoFallo from ActividadEjecucion a
            where a.idTarea in :ids and a.tipoFallo is not null
            """)
    List<FalloRegistrado> fallosDeTareas(@Param("ids") Collection<Integer> ids);

    /** DEC-61: la categoría de cada análisis fallido, tomada de su evento FALLIDO; sin entrada si no se registró */
    default Map<Integer, TipoFallo> fallosPorEvaluacion(Collection<Integer> ids) {
        return ids.isEmpty() ? Map.of() : porId(fallosDeEvaluaciones(ids));
    }

    /** DEC-61: la categoría de cada tarea fallida, tomada de su evento FALLIDO; sin entrada si no se registró */
    default Map<Integer, TipoFallo> fallosPorTarea(Collection<Integer> ids) {
        return ids.isEmpty() ? Map.of() : porId(fallosDeTareas(ids));
    }

    private static Map<Integer, TipoFallo> porId(List<FalloRegistrado> fallos) {
        return fallos.stream().collect(Collectors.toMap(FalloRegistrado::getId, FalloRegistrado::getTipoFallo, (a, b) -> b));
    }

    interface FalloRegistrado {
        Integer getId();

        TipoFallo getTipoFallo();
    }
}
