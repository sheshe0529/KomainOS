package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.EstadoDetalleOrden;
import com.komainos.mantenimiento.model.EstadoOrden;
import com.komainos.mantenimiento.model.Orden;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrdenRepositorio extends JpaRepository<Orden, Integer>, JpaSpecificationExecutor<Orden> {

    /** Se pide antes de insertar porque el código de la orden incluye el id (DEC-07) */
    @Query(value = "select nextval('\"KomainOS\".orden_id_orden_seq')", nativeQuery = true)
    Integer siguienteId();

    @Override
    @EntityGraph(attributePaths = {"servidor", "grupo", "nivelCriticidad"})
    Page<Orden> findAll(Specification<Orden> especificacion, Pageable paginacion);

    @EntityGraph(attributePaths = {"servidor.responsable", "grupo", "nivelCriticidad", "usuarioSolicitante",
            "detalles.servidor"})
    Optional<Orden> findConDetalleById(Integer id);

    /* Enumerados siempre como parámetro: como literal Hibernate los castea a un tipo que PostgreSQL no tiene (DEC-26) */

    @Query("""
            select distinct o from Orden o join o.detalles d
            where d.servidor.id = :idServidor
              and d.estado <> :excluido
              and o.estado in :estados
            """)
    List<Orden> findDelServidorEnEstados(@Param("idServidor") Integer idServidor,
                                         @Param("estados") Collection<EstadoOrden> estados,
                                         @Param("excluido") EstadoDetalleOrden excluido);

    default List<Orden> findDelServidorEnEstados(Integer idServidor, Collection<EstadoOrden> estados) {
        return findDelServidorEnEstados(idServidor, estados, EstadoDetalleOrden.NO_INICIADO);
    }

    @Query("""
            select count(o) > 0 from Orden o join o.detalles d
            where d.servidor.id = :idServidor
              and d.estado <> :excluido
              and o.estado in :estados
            """)
    boolean existeDelServidorEnEstados(@Param("idServidor") Integer idServidor,
                                       @Param("estados") Collection<EstadoOrden> estados,
                                       @Param("excluido") EstadoDetalleOrden excluido);

    default boolean existeDelServidorEnEstados(Integer idServidor, Collection<EstadoOrden> estados) {
        return existeDelServidorEnEstados(idServidor, estados, EstadoDetalleOrden.NO_INICIADO);
    }

    /** Evita duplicar el ciclo automático (RF27) */
    boolean existsByServidorIdAndEstadoIn(Integer idServidor, Collection<EstadoOrden> estados);

    boolean existsByGrupoIdAndEstadoIn(Integer idGrupo, Collection<EstadoOrden> estados);

    boolean existsByServidorId(Integer idServidor);

    boolean existsByGrupoId(Integer idGrupo);

    List<Orden> findByGrupoIdAndEstadoIn(Integer idGrupo, Collection<EstadoOrden> estados);
}
