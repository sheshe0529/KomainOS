package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.CierreOrden;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CierreOrdenRepositorio extends JpaRepository<CierreOrden, Integer> {

    interface FactorAplicable { Integer getId(); java.math.BigDecimal getFactor(); }
    @Query(value="select id_factor_ciclo as id,factor from factor_ciclo where resultado::text=:resultado",nativeQuery=true)
    FactorAplicable factor(@Param("resultado") String resultado);
    @Query("select c from CierreOrden c where (:servidor is not null and c.orden.servidor.id=:servidor) or (:grupo is not null and c.orden.grupo.id=:grupo) order by c.fechaCierre desc")
    java.util.List<CierreOrden> ultimos(@Param("servidor") Integer servidor,@Param("grupo") Integer grupo,org.springframework.data.domain.Pageable p);

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
