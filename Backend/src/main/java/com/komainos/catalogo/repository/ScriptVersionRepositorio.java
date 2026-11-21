package com.komainos.catalogo.repository;

import com.komainos.catalogo.model.ScriptVersion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ScriptVersionRepositorio extends JpaRepository<ScriptVersion, Integer> {

    @EntityGraph(attributePaths = {"implementacion.script", "implementacion.versionSistemaOperativo.sistemaOperativo",
            "parametros"})
    Optional<ScriptVersion> findConDetalleById(Integer id);

    @Query("select max(v.numeroVersion) from ScriptVersion v where v.implementacion.id = :id")
    Integer ultimoNumero(@Param("id") Integer idImplementacion);
}
