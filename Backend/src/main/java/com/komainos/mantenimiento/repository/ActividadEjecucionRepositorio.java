package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.ActividadEjecucion;
import com.komainos.mantenimiento.model.FaseActividad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ActividadEjecucionRepositorio extends JpaRepository<ActividadEjecucion, Integer> {

    List<ActividadEjecucion> findByIdDetalleInOrderByFechaHoraAscIdAsc(Collection<Integer> idsDetalle);

    List<ActividadEjecucion> findByIdTareaOrderByFechaHoraAscIdAsc(Integer idTarea);

    /** Los pasos de la ejecución en ese servidor que no son de una tarea: inicio, comprobación de acceso y cierre */
    List<ActividadEjecucion> findByIdDetalleAndIdTareaIsNullAndFaseOrderByFechaHoraAscIdAsc(Integer idDetalle,
                                                                                           FaseActividad fase);

    List<ActividadEjecucion> findByIdEvaluacionOrderByFechaHoraAscIdAsc(Integer idEvaluacion);
}
