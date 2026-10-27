package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.ValidacionFuncional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ValidacionFuncionalRepositorio extends JpaRepository<ValidacionFuncional, Integer> {

    /** Una por orden (uq_validacion_funcional_orden): un grupo tiene un único responsable */
    @EntityGraph(attributePaths = {"responsable"})
    Optional<ValidacionFuncional> findByOrdenId(Integer idOrden);
}
