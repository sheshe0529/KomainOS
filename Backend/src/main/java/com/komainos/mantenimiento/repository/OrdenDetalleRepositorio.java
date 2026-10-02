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
