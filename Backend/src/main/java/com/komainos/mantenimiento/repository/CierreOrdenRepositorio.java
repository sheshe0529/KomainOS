package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.CierreOrden;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface CierreOrdenRepositorio extends JpaRepository<CierreOrden, Integer> {

    /** Factor del resultado del ciclo (RF64); factor_ciclo pertenece a la planificación */
    @Query(value = "select id_factor_ciclo as id, factor from factor_ciclo where resultado::text = :resultado",
            nativeQuery = true)
    FactorAplicable factor(@Param("resultado") String resultado);

    /** Cierres anteriores para la racha estable (RF65), del más reciente al más antiguo */
    @Query("select c from CierreOrden c where c.orden.servidor.id = :idServidor order by c.fechaCierre desc")
    List<CierreOrden> ultimosDeServidor(@Param("idServidor") Integer idServidor, Pageable pagina);

    @Query("select c from CierreOrden c where c.orden.grupo.id = :idGrupo order by c.fechaCierre desc")
    List<CierreOrden> ultimosDeGrupo(@Param("idGrupo") Integer idGrupo, Pageable pagina);

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

    interface FactorAplicable {

        Integer getId();

        BigDecimal getFactor();
    }
}
