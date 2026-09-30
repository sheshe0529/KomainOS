package com.komainos.inventario.repository;

import com.komainos.inventario.model.VersionSistemaOperativo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VersionSistemaOperativoRepositorio extends JpaRepository<VersionSistemaOperativo, Integer> {

    @EntityGraph(attributePaths = "sistemaOperativo")
    Optional<VersionSistemaOperativo> findConSistemaById(Integer id);

    boolean existsBySistemaOperativoIdAndVersionIgnoreCase(Integer idSistemaOperativo, String version);

    boolean existsBySistemaOperativoIdAndVersionIgnoreCaseAndIdNot(Integer idSistemaOperativo, String version, Integer id);
}
