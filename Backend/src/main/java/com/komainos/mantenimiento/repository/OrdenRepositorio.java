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

    /**
     * Siguiente id de la secuencia de identidad de la tabla. Se pide antes de
     * insertar porque el codigo de la orden lo incluye (DEC-07).
     */
    @Query(value = "select nextval('\"KomainOS\".orden_id_orden_seq')", nativeQuery = true)
    Integer siguienteId();

    @Override
    @EntityGraph(attributePaths = {"servidor", "grupo", "nivelCriticidad"})
    Page<Orden> findAll(Specification<Orden> especificacion, Pageable paginacion);

    @EntityGraph(attributePaths = {"servidor.responsable", "grupo", "nivelCriticidad", "usuarioSolicitante",
            "detalles.servidor"})
    Optional<Orden> findConDetalleById(Integer id);

    /*
     * Los enumerados se pasan siempre como parametro y nunca como literal en
     * JPQL: Hibernate escribe el literal con un cast al nombre de la clase Java
     * ('X'::EstadoDetalleOrden), tipo que no existe en PostgreSQL. Como
     * parametro se envia sin tipo y la base lo resuelve por la columna.
     */

    /** Ordenes en ciertos estados en las que participa un servidor (detalle vigente). */
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

    /** Ordenes individuales de un servidor en ciertos estados (RF27: no duplicar el ciclo). */
    boolean existsByServidorIdAndEstadoIn(Integer idServidor, Collection<EstadoOrden> estados);

    boolean existsByGrupoIdAndEstadoIn(Integer idGrupo, Collection<EstadoOrden> estados);

    boolean existsByServidorId(Integer idServidor);

    boolean existsByGrupoId(Integer idGrupo);

    /** Ordenes pendientes de un grupo (DEC-18: replanificar si cambia su ventana). */
    List<Orden> findByGrupoIdAndEstadoIn(Integer idGrupo, Collection<EstadoOrden> estados);
}
