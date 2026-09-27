package com.komainos.inventario.infra;

import com.komainos.inventario.dominio.VersionSistemaOperativo;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VersionSistemaOperativoRepositorio extends JpaRepository<VersionSistemaOperativo, Integer> {

    @EntityGraph(attributePaths = "sistemaOperativo")
    Optional<VersionSistemaOperativo> findConSistemaById(Integer id);

    boolean existsBySistemaOperativoIdAndVersionIgnoreCase(Integer idSistemaOperativo, String version);

    boolean existsBySistemaOperativoIdAndVersionIgnoreCaseAndIdNot(Integer idSistemaOperativo, String version, Integer id);
}
