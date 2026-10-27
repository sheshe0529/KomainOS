package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.Mop;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MopRepositorio extends JpaRepository<Mop, Integer> {

    @EntityGraph(attributePaths = {"detalle.servidor"})
    Optional<Mop> findByDetalleId(Integer idDetalle);

    @EntityGraph(attributePaths = {"detalle.servidor"})
    List<Mop> findByDetalleOrdenIdOrderByDetallePosicionEjecucionAsc(Integer idOrden);

    boolean existsByDetalleId(Integer idDetalle);
}
