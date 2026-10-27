package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.EstadoTareaMop;
import com.komainos.mantenimiento.model.MopTarea;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface MopTareaRepositorio extends JpaRepository<MopTarea, Integer>, JpaSpecificationExecutor<MopTarea> {

    /** RF26: tareas ejecutadas o fijadas con una versión del script */
    @EntityGraph(attributePaths = {"mop.detalle.orden", "mop.detalle.servidor", "tarea"})
    Page<MopTarea> findByVersionIdOrderByIdDesc(Integer idVersion, Pageable pagina);

    /** Historial de ejecución de scripts (R3.4) */
    @Override
    @EntityGraph(attributePaths = {"mop.detalle.orden", "mop.detalle.servidor", "tarea",
            "version.implementacion.script", "version.implementacion.versionSistemaOperativo.sistemaOperativo"})
    Page<MopTarea> findAll(Specification<MopTarea> filtro, Pageable pagina);

    @EntityGraph(attributePaths = {"mop.detalle.orden", "mop.detalle.servidor", "tarea",
            "version.implementacion.script", "version.implementacion.versionSistemaOperativo.sistemaOperativo"})
    Optional<MopTarea> findConDetalleById(Integer id);

    List<MopTarea> findByEstado(EstadoTareaMop estado);
}
