package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.ValidacionTecnica;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ValidacionTecnicaRepositorio extends JpaRepository<ValidacionTecnica, Integer> {

    @EntityGraph(attributePaths = {"detalle.servidor"})
    List<ValidacionTecnica> findByDetalleOrdenId(Integer idOrden);

    boolean existsByDetalleId(Integer idDetalle);
}
