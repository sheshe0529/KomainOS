package com.komainos.inventario.repository;

import com.komainos.inventario.model.Metrica;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MetricaRepositorio extends JpaRepository<Metrica, Integer> {

    List<Metrica> findAllByOrderByNombreAsc();

    boolean existsByCodigoIgnoreCase(String codigo);

    /** medicion_metrica no tiene entidad todavía: la registra la ejecución de los scripts de análisis */
    @Query(value = "select exists (select 1 from \"KomainOS\".medicion_metrica where id_metrica = :id)", nativeQuery = true)
    boolean tieneMediciones(@Param("id") Integer idMetrica);
}
