package com.komainos.credencial.repository;

import com.komainos.credencial.model.CredencialDocumental;
import com.komainos.credencial.model.EstadoCredencial;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CredencialDocumentalRepositorio extends JpaRepository<CredencialDocumental, Integer> {

    /** La principal primero y las vigentes antes que las revocadas: el enumerado de PostgreSQL ordena por su declaración */
    @EntityGraph(attributePaths = {"versiones"})
    List<CredencialDocumental> findByServidorIdOrderByPrincipalDescEstadoAscNombreAsc(Integer idServidor);

    Optional<CredencialDocumental> findByServidorIdAndPrincipalTrue(Integer idServidor);

    @EntityGraph(attributePaths = {"versiones"})
    List<CredencialDocumental> findByServidorIdInAndPrincipalTrue(Collection<Integer> idsServidores);

    @EntityGraph(attributePaths = {"versiones", "servidor"})
    List<CredencialDocumental> findByEstadoOrderByServidorHostnameAscPrincipalDescNombreAsc(EstadoCredencial estado);
}
