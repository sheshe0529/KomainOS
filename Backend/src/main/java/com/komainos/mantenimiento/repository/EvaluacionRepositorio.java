package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.EstadoEvaluacion;
import com.komainos.mantenimiento.model.Evaluacion;
import com.komainos.mantenimiento.model.TipoEvaluacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EvaluacionRepositorio extends JpaRepository<Evaluacion, Integer> {

    @EntityGraph(attributePaths = {"servidor", "version.implementacion.script", "detalle", "revisionGrupo"})
    Optional<Evaluacion> findConDetalleById(Integer id);

    @EntityGraph(attributePaths = {"servidor", "version.implementacion.script"})
    List<Evaluacion> findByDetalleIdOrderByFechaInicioAsc(Integer idDetalle);

    @EntityGraph(attributePaths = {"servidor", "version.implementacion.script"})
    List<Evaluacion> findByDetalleIdInOrderByFechaInicioAsc(Collection<Integer> idsDetalle);

    @EntityGraph(attributePaths = {"servidor", "version.implementacion.script"})
    Page<Evaluacion> findByServidorIdAndRevisionGrupoIsNullOrderByFechaInicioDesc(Integer idServidor, Pageable pagina);

    Optional<Evaluacion> findFirstByServidorIdAndTipoAndRevisionGrupoIsNullOrderByFechaInicioDesc(
            Integer idServidor, TipoEvaluacion tipo);

    /** Las evaluaciones posteriores de una orden: si existen, ya se inició la validación técnica (RF55) */
    @Query("select e from Evaluacion e where e.detalle.orden.id = :idOrden and e.tipo = :tipo order by e.id")
    List<Evaluacion> deOrden(@Param("idOrden") Integer idOrden, @Param("tipo") TipoEvaluacion tipo);

    List<Evaluacion> findByEstado(EstadoEvaluacion estado);

    /**
     * Los análisis de un grupo agrupados por evento, del más reciente al más antiguo: cada revisión periódica del grupo
     * y, de cada orden del grupo, su evaluación previa y su validación técnica
     */
    @Query(value = """
            select cast(e.tipo as text) as "tipo", e.id_revision_periodica_grupo as "idRevision", d.id_orden as "idOrden"
              from "KomainOS".evaluacion e
              left join "KomainOS".revision_periodica_grupo r on r.id_revision_periodica_grupo = e.id_revision_periodica_grupo
              left join "KomainOS".orden_detalle d on d.id_orden_detalle = e.id_orden_detalle
              left join "KomainOS".orden o on o.id_orden = d.id_orden
             where r.id_grupo_mantenimiento = :idGrupo or o.id_grupo_mantenimiento = :idGrupo
             group by e.tipo, e.id_revision_periodica_grupo, d.id_orden
             order by min(e.fecha_inicio) desc, min(e.id_evaluacion) desc
            """, countQuery = """
            select count(*) from (
            select 1
              from "KomainOS".evaluacion e
              left join "KomainOS".revision_periodica_grupo r on r.id_revision_periodica_grupo = e.id_revision_periodica_grupo
              left join "KomainOS".orden_detalle d on d.id_orden_detalle = e.id_orden_detalle
              left join "KomainOS".orden o on o.id_orden = d.id_orden
             where r.id_grupo_mantenimiento = :idGrupo or o.id_grupo_mantenimiento = :idGrupo
             group by e.tipo, e.id_revision_periodica_grupo, d.id_orden) eventos
            """, nativeQuery = true)
    Page<EventoAnalisisGrupo> eventosDeGrupo(@Param("idGrupo") Integer idGrupo, Pageable pagina);

    @EntityGraph(attributePaths = {"servidor", "version.implementacion.script", "revisionGrupo"})
    List<Evaluacion> findByRevisionGrupoIdInOrderByFechaInicioAsc(Collection<Integer> idsRevision);

    @EntityGraph(attributePaths = {"servidor", "version.implementacion.script", "detalle.orden"})
    List<Evaluacion> findByDetalleOrdenIdInOrderByFechaInicioAsc(Collection<Integer> idsOrden);

    /** Un evento de análisis de un grupo: una revisión periódica, o un tipo de análisis de una orden */
    interface EventoAnalisisGrupo {

        String getTipo();

        Integer getIdRevision();

        Integer getIdOrden();
    }
}
