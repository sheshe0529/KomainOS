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

    @EntityGraph(attributePaths = {"servidor", "version.implementacion.script", "detalle"})
    Optional<Evaluacion> findConDetalleById(Integer id);

    @EntityGraph(attributePaths = {"servidor", "version.implementacion.script"})
    List<Evaluacion> findByDetalleIdOrderByFechaInicioAsc(Integer idDetalle);

    @EntityGraph(attributePaths = {"servidor", "version.implementacion.script"})
    List<Evaluacion> findByDetalleIdInOrderByFechaInicioAsc(Collection<Integer> idsDetalle);

    @EntityGraph(attributePaths = {"servidor", "version.implementacion.script"})
    Page<Evaluacion> findByServidorIdOrderByFechaInicioDesc(Integer idServidor, Pageable pagina);

    Optional<Evaluacion> findFirstByServidorIdAndTipoOrderByFechaInicioDesc(Integer idServidor, TipoEvaluacion tipo);

    /** Las evaluaciones posteriores de una orden: si existen, ya se inició la validación técnica (RF55) */
    @Query("select e from Evaluacion e where e.detalle.orden.id = :idOrden and e.tipo = :tipo order by e.id")
    List<Evaluacion> deOrden(@Param("idOrden") Integer idOrden, @Param("tipo") TipoEvaluacion tipo);

    List<Evaluacion> findByEstado(EstadoEvaluacion estado);
}
