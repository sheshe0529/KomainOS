package com.komainos.catalogo.repository;

import com.komainos.catalogo.model.ImplementacionScript;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImplementacionScriptRepositorio extends JpaRepository<ImplementacionScript, Integer> {

    @EntityGraph(attributePaths = {"script", "versionSistemaOperativo.sistemaOperativo", "versiones"})
    Optional<ImplementacionScript> findConDetalleById(Integer id);

    @EntityGraph(attributePaths = {"script"})
    List<ImplementacionScript> findByVersionSistemaOperativoId(Integer idVersionSistemaOperativo);
}
