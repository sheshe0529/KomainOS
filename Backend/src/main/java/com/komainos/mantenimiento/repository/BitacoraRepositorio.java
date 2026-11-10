package com.komainos.mantenimiento.repository;

import com.komainos.inventario.model.NivelAlerta;
import com.komainos.mantenimiento.model.EstadoEvaluacion;
import com.komainos.mantenimiento.model.EstadoTareaMop;
import com.komainos.mantenimiento.model.EstadoValidacionFuncional;
import com.komainos.mantenimiento.model.Orden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.ResultadoValidacionTecnica;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Lecturas por lote para los resúmenes de mantenimiento (RF53). */
public interface BitacoraRepositorio extends Repository<Orden, Integer> {

    @EntityGraph(attributePaths = {"grupo", "historial"})
    List<Orden> findByIdIn(Collection<Integer> idsOrden);

    @EntityGraph(attributePaths = "servidor")
    @Query("""
            select d from OrdenDetalle d
            where d.orden.id in :idsOrden
              and (:idServidor is null or d.servidor.id = :idServidor)
              and (:todos = true or d.servidor.responsable.id = :idUsuario)
            order by d.posicionEjecucion
            """)
    List<OrdenDetalle> participaciones(@Param("idsOrden") Collection<Integer> idsOrden,
                                       @Param("idServidor") Integer idServidor,
                                       @Param("todos") boolean todos, @Param("idUsuario") Integer idUsuario);

    @Query("""
            select t.mop.detalle.id as idDetalle, t.tarea.nombre as nombre, t.estado as estado,
                   t.fechaInicio as inicio, t.fechaFin as fin
            from MopTarea t
            where t.mop.detalle.id in :idsDetalle
            order by t.ordenEjecucion
            """)
    List<Tarea> tareas(@Param("idsDetalle") Collection<Integer> idsDetalle);

    /** Niveles de los umbrales de la última evaluación finalizada por participación y tipo. */
    @Query("""
            select e.detalle.id as idDetalle, e.tipo as tipo, u.nivel as nivel
            from Evaluacion e
              left join e.mediciones m
              left join m.umbrales u
            where e.detalle.id in :idsDetalle and e.estado = :finalizada
              and not exists (
                select posterior.id from Evaluacion posterior
                where posterior.detalle = e.detalle and posterior.tipo = e.tipo and posterior.estado = :finalizada
                  and (posterior.fechaInicio > e.fechaInicio
                    or (posterior.fechaInicio = e.fechaInicio and posterior.id > e.id))
              )
            """)
    List<NivelEvaluacion> niveles(@Param("idsDetalle") Collection<Integer> idsDetalle,
                                  @Param("finalizada") EstadoEvaluacion finalizada);

    @Query("""
            select v.detalle.id as idDetalle, v.resultado as resultado
            from ValidacionTecnica v where v.detalle.id in :idsDetalle
            """)
    List<ValidacionTecnica> validacionesTecnicas(@Param("idsDetalle") Collection<Integer> idsDetalle);

    @Query("""
            select v.orden.id as idOrden, v.estado as estado
            from ValidacionFuncional v
            where v.orden.id in :idsOrden and (:todos = true or v.responsable.id = :idUsuario)
            """)
    List<ValidacionFuncional> validacionesFuncionales(@Param("idsOrden") Collection<Integer> idsOrden,
                                                      @Param("todos") boolean todos,
                                                      @Param("idUsuario") Integer idUsuario);

    @Query("""
            select i.detalle.id as idDetalle, count(i) as cantidad
            from Incidencia i where i.detalle.id in :idsDetalle
            group by i.detalle.id
            """)
    List<Incidencias> incidencias(@Param("idsDetalle") Collection<Integer> idsDetalle);

    interface Tarea {
        Integer getIdDetalle();
        String getNombre();
        EstadoTareaMop getEstado();
        Instant getInicio();
        Instant getFin();
    }

    interface NivelEvaluacion {
        Integer getIdDetalle();
        TipoEvaluacion getTipo();
        NivelAlerta getNivel();
    }

    interface ValidacionTecnica {
        Integer getIdDetalle();
        ResultadoValidacionTecnica getResultado();
    }

    interface ValidacionFuncional {
        Integer getIdOrden();
        EstadoValidacionFuncional getEstado();
    }

    interface Incidencias {
        Integer getIdDetalle();
        long getCantidad();
    }
}
