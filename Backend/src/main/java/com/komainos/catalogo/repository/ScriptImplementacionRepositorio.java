package com.komainos.catalogo.repository;

import com.komainos.catalogo.model.ScriptImplementacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ScriptImplementacionRepositorio extends JpaRepository<ScriptImplementacion, Integer> {

    @EntityGraph(attributePaths = {"script", "versionSistemaOperativo.sistemaOperativo", "versiones"})
    Optional<ScriptImplementacion> findConDetalleById(Integer id);

    @EntityGraph(attributePaths = {"script"})
    List<ScriptImplementacion> findByVersionSistemaOperativoId(Integer idVersionSistemaOperativo);
}
