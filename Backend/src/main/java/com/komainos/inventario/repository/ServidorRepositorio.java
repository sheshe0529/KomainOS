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

    /** El grafo evita cuatro consultas adicionales por fila del listado */
    @Override
    @EntityGraph(attributePaths = {"versionSistemaOperativo.sistemaOperativo", "entorno", "nivelCriticidad", "responsable"})
    Page<Servidor> findAll(Specification<Servidor> especificacion, Pageable paginacion);

    @Override
    @EntityGraph(attributePaths = {"versionSistemaOperativo.sistemaOperativo", "entorno", "nivelCriticidad", "responsable"})
    List<Servidor> findAll(Specification<Servidor> especificacion, Sort orden);

    @EntityGraph(attributePaths = {"versionSistemaOperativo.sistemaOperativo", "entorno", "nivelCriticidad",
            "responsable", "ventanas", "direcciones"})
    Optional<Servidor> findConDetalleById(Integer id);

    @EntityGraph(attributePaths = {"versionSistemaOperativo.sistemaOperativo", "entorno", "nivelCriticidad",
            "responsable", "ventanas"})
    List<Servidor> findConDetalleByIdIn(Collection<Integer> ids);

    @EntityGraph(attributePaths = {"versionSistemaOperativo.sistemaOperativo", "entorno", "nivelCriticidad", "responsable"})
    List<Servidor> findByEstadoNotOrderByHostnameAsc(EstadoServidor estado);

    @EntityGraph(attributePaths = {"versionSistemaOperativo.sistemaOperativo", "entorno", "nivelCriticidad", "responsable"})
    @Query("""
            select s from Servidor s
            where lower(s.hostname) in :hostnames
               or exists (select 1 from DireccionIp d where d.servidor = s and d.direccion in :direcciones)""")
    List<Servidor> findCoincidentes(@Param("hostnames") Collection<String> hostnames,
                                    @Param("direcciones") Collection<String> direcciones);

    boolean existsByHostnameIgnoreCase(String hostname);

    boolean existsByHostnameIgnoreCaseAndIdNot(String hostname, Integer id);


    long countByEstado(EstadoServidor estado);

    long countByResponsableIdAndEstadoNot(Integer idResponsable, EstadoServidor estado);
}
