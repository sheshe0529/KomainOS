package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.OrdenDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface OrdenDetalleRepositorio extends JpaRepository<OrdenDetalle, Integer> {
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"orden.nivelCriticidad","servidor"})
 @org.springframework.data.jpa.repository.Query("select d from OrdenDetalle d where d.estado in :estados order by case when d.orden.origen in :altas then 0 else 1 end,d.orden.nivelCriticidad.prioridad,d.orden.fechaInicioProgramada,d.posicionEjecucion")
 org.springframework.data.domain.Page<OrdenDetalle> cola(@org.springframework.data.repository.query.Param("estados") java.util.Set<EstadoDetalleOrden> estados,@org.springframework.data.repository.query.Param("altas")java.util.Set<com.komainos.mantenimiento.model.OrigenOrden> altas,org.springframework.data.domain.Pageable p);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths={"orden","servidor"})
    java.util.List<OrdenDetalle> findByEstado(EstadoDetalleOrden estado);


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
