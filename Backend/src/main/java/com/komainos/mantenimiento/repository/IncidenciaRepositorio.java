package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.Incidencia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface IncidenciaRepositorio extends JpaRepository<Incidencia, Integer>, JpaSpecificationExecutor<Incidencia> {

    @EntityGraph(attributePaths = {"detalle.servidor", "detalle.orden", "usuarioAtencion"})
    List<Incidencia> findByDetalleOrdenIdOrderByFechaAperturaAsc(Integer idOrden);

    @Override
    @EntityGraph(attributePaths = {"detalle.servidor", "detalle.orden", "usuarioAtencion"})
    Page<Incidencia> findAll(Specification<Incidencia> filtro, Pageable pagina);

    @Override
    @EntityGraph(attributePaths = {"detalle.servidor", "detalle.orden", "usuarioAtencion"})
    Optional<Incidencia> findById(Integer id);
}
