package com.komainos.inventario.infra;

import com.komainos.inventario.dominio.SistemaOperativo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SistemaOperativoRepositorio extends JpaRepository<SistemaOperativo, Integer> {

    /** Trae las versiones en la misma consulta: el catalogo se muestra completo. */
    @EntityGraph(attributePaths = "versiones")
    List<SistemaOperativo> findAllByOrderByNombreAsc();

    @EntityGraph(attributePaths = "versiones")
    Optional<SistemaOperativo> findConVersionesById(Integer id);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);
}
