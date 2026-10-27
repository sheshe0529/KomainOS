package com.komainos.catalogo.repository;

import com.komainos.catalogo.model.VersionScript;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VersionScriptRepositorio extends JpaRepository<VersionScript, Integer> {

    @EntityGraph(attributePaths = {"implementacion.script", "implementacion.versionSistemaOperativo.sistemaOperativo",
            "parametros"})
    Optional<VersionScript> findConDetalleById(Integer id);

    @Query("select max(v.numeroVersion) from VersionScript v where v.implementacion.id = :id")
    Integer ultimoNumero(@Param("id") Integer idImplementacion);
}
