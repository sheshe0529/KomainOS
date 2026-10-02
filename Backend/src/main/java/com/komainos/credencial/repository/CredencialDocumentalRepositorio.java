package com.komainos.credencial.repository;

import com.komainos.credencial.model.CredencialDocumental;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CredencialDocumentalRepositorio extends JpaRepository<CredencialDocumental, Integer> {

    /** Vigentes primero: el enumerado de PostgreSQL ordena por su declaración */
    @EntityGraph(attributePaths = {"versiones"})
    List<CredencialDocumental> findByServidorIdOrderByEstadoAscNombreAsc(Integer idServidor);
}
