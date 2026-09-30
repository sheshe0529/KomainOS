package com.komainos.inventario.repository;

import com.komainos.inventario.model.EstadoServidor;
import com.komainos.inventario.model.Servidor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServidorRepositorio extends JpaRepository<Servidor, Integer>, JpaSpecificationExecutor<Servidor> {

    /**
     * El grafo trae en la misma consulta todo lo que el listado muestra; sin
     * el, cada fila dispararia cuatro consultas adicionales.
     */
    @Override
    @EntityGraph(attributePaths = {"versionSistemaOperativo.sistemaOperativo", "entorno", "nivelCriticidad", "responsable"})
    Page<Servidor> findAll(Specification<Servidor> especificacion, Pageable paginacion);

    /** Exportacion (RF13): el mismo filtro del listado, sin paginar. */
    @Override
    @EntityGraph(attributePaths = {"versionSistemaOperativo.sistemaOperativo", "entorno", "nivelCriticidad", "responsable"})
    List<Servidor> findAll(Specification<Servidor> especificacion, Sort orden);

    @EntityGraph(attributePaths = {"versionSistemaOperativo.sistemaOperativo", "entorno", "nivelCriticidad",
            "responsable", "ventanas"})
    Optional<Servidor> findConDetalleById(Integer id);

    @EntityGraph(attributePaths = {"versionSistemaOperativo.sistemaOperativo", "entorno", "nivelCriticidad",
            "responsable", "ventanas"})
    List<Servidor> findConDetalleByIdIn(Collection<Integer> ids);

    @EntityGraph(attributePaths = {"versionSistemaOperativo.sistemaOperativo", "entorno", "nivelCriticidad", "responsable"})
    List<Servidor> findByEstadoNotOrderByHostnameAsc(EstadoServidor estado);

    /**
     * Importacion (RF12): servidores que ya usan alguno de los hostnames (en
     * minusculas) o IP del archivo, para clasificar los duplicados en una sola
     * consulta.
     */
    @EntityGraph(attributePaths = {"versionSistemaOperativo.sistemaOperativo", "entorno", "nivelCriticidad", "responsable"})
    @Query("select s from Servidor s where lower(s.hostname) in :hostnames or s.direccionIp in :direcciones")
    List<Servidor> findCoincidentes(@Param("hostnames") Collection<String> hostnames,
                                    @Param("direcciones") Collection<String> direcciones);

    boolean existsByHostnameIgnoreCase(String hostname);

    boolean existsByHostnameIgnoreCaseAndIdNot(String hostname, Integer id);

    boolean existsByDireccionIp(String direccionIp);

    boolean existsByDireccionIpAndIdNot(String direccionIp, Integer id);

    long countByEstado(EstadoServidor estado);

    long countByResponsableIdAndEstadoNot(Integer idResponsable, EstadoServidor estado);
}
