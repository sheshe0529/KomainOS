package com.komainos.mantenimiento.infra;

import com.komainos.mantenimiento.dominio.EstadoDetalleOrden;
import com.komainos.mantenimiento.dominio.EstadoOrden;
import com.komainos.mantenimiento.dominio.OrdenDetalle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface OrdenDetalleRepositorio extends JpaRepository<OrdenDetalle, Integer> {

    /**
     * Reservas vigentes que se solapan con [desde, hasta): detalles de ordenes
     * que ocupan el cronograma (especificacion del algoritmo, seccion 3). Los
     * enumerados van como parametro (DEC-26).
     */
    @Query("""
            select new com.komainos.mantenimiento.infra.FilaReserva(
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
