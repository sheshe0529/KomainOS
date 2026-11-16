package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.ActividadEjecucion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Cada evento tiene un solo origen (DEC-60): la ficha de la orden consulta en bloque los de cada tipo de origen */
public interface ActividadEjecucionRepositorio extends JpaRepository<ActividadEjecucion, Integer> {

    /** Los pasos generales de la ejecución en esos servidores: inicio, comprobación de acceso y cierre */
    List<ActividadEjecucion> findByIdDetalleInOrderByFechaHoraAscIdAsc(Collection<Integer> idsDetalle);

    List<ActividadEjecucion> findByIdEvaluacionInOrderByFechaHoraAscIdAsc(Collection<Integer> idsEvaluacion);

    List<ActividadEjecucion> findByIdTareaInOrderByFechaHoraAscIdAsc(Collection<Integer> idsTarea);

    List<ActividadEjecucion> findByIdDetalleOrderByFechaHoraAscIdAsc(Integer idDetalle);

    List<ActividadEjecucion> findByIdTareaOrderByFechaHoraAscIdAsc(Integer idTarea);

    List<ActividadEjecucion> findByIdEvaluacionOrderByFechaHoraAscIdAsc(Integer idEvaluacion);
}
