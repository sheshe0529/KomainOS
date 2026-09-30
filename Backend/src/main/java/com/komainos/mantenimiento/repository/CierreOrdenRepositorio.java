package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.CierreOrden;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CierreOrdenRepositorio extends JpaRepository<CierreOrden, Integer> {

    /** Ultimo cierre de las ordenes individuales de un servidor que aun no genero su ciclo siguiente. */
    @Query("""
            select c from CierreOrden c
            where c.orden.servidor.id = :idServidor and c.ordenSiguiente is null
            order by c.fechaCierre desc limit 1
            """)
    Optional<CierreOrden> findPendienteDeServidor(@Param("idServidor") Integer idServidor);

    @Query("""
            select c from CierreOrden c
            where c.orden.grupo.id = :idGrupo and c.ordenSiguiente is null
            order by c.fechaCierre desc limit 1
            """)
    Optional<CierreOrden> findPendienteDeGrupo(@Param("idGrupo") Integer idGrupo);
}
