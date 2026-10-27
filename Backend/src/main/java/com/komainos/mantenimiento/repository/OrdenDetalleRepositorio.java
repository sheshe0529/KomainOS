package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import com.komainos.mantenimiento.model.OrigenOrden;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrdenDetalleRepositorio extends JpaRepository<OrdenDetalle, Integer> {

    /**
     * Cola de ejecución (RF35): prioridad alta primero, luego la criticidad, el inicio programado y la posición en el
     * grupo. Los enumerados van como parámetro (DEC-26).
     */
    @EntityGraph(attributePaths = {"orden.nivelCriticidad", "servidor"})
    @Query("""
            select d from OrdenDetalle d
            where d.estado in :estados
            order by case when d.orden.origen in :altas then 0 else 1 end, d.orden.nivelCriticidad.prioridad,
                d.orden.fechaInicioProgramada, d.posicionEjecucion
            """)
    Page<OrdenDetalle> cola(@Param("estados") Collection<EstadoDetalleOrden> estados,
                            @Param("altas") Collection<OrigenOrden> altas, Pageable pagina);

    @EntityGraph(attributePaths = {"orden", "servidor"})
    List<OrdenDetalle> findByEstado(EstadoDetalleOrden estado);

    @EntityGraph(attributePaths = {"orden", "servidor"})
    Optional<OrdenDetalle> findConOrdenById(Integer id);

    /** Los enumerados van como parámetro (DEC-26) */
    @Query("""
            select new com.komainos.mantenimiento.repository.FilaReserva(
                d.servidor.id, d.fechaPrevistaInicio, d.fechaPrevistaFin, o.id, o.codigo)
            from OrdenDetalle d join d.orden o
            where o.estado in :estados
              and d.estado <> :excluido
              and d.fechaPrevistaInicio < :hasta
              and d.fechaPrevistaFin > :desde
            """)
    List<FilaReserva> findReservas(@Param("estados") Collection<EstadoOrden> estados,
                                   @Param("excluido") EstadoDetalleOrden excluido,
                                   @Param("desde") Instant desde,
                                   @Param("hasta") Instant hasta);
}
