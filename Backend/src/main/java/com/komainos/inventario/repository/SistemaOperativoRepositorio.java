package com.komainos.inventario.repository;

import com.komainos.inventario.model.SistemaOperativo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SistemaOperativoRepositorio extends JpaRepository<SistemaOperativo, Integer> {

    @EntityGraph(attributePaths = "versiones")
    List<SistemaOperativo> findAllByOrderByNombreAsc();

    @EntityGraph(attributePaths = "versiones")
    Optional<SistemaOperativo> findConVersionesById(Integer id);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);
}
