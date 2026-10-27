package com.komainos.mantenimiento.repository;

import com.komainos.catalogo.model.NivelAfectacion;
import com.komainos.mantenimiento.model.ReglaPoliticaCambio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReglaPoliticaCambioRepositorio extends JpaRepository<ReglaPoliticaCambio, Integer> {

    @EntityGraph(attributePaths = {"entorno"})
    Optional<ReglaPoliticaCambio> findByEntornoIdAndAfectacion(Integer idEntorno, NivelAfectacion afectacion);

    @EntityGraph(attributePaths = {"entorno"})
    List<ReglaPoliticaCambio> findByEntornoId(Integer idEntorno);

    @Override
    @EntityGraph(attributePaths = {"entorno"})
    Page<ReglaPoliticaCambio> findAll(Pageable pagina);
}
