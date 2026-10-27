package com.komainos.catalogo.repository;

import com.komainos.catalogo.model.Script;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface ScriptRepositorio extends JpaRepository<Script, Integer>, JpaSpecificationExecutor<Script> {

    /**
     * Serializa las publicaciones del catálogo hasta el fin de la transacción: el número de versión y la unicidad del
     * análisis por versión de sistema operativo (RF24) se comprueban sin carreras entre administradores.
     */
    @Query(value = "select count(*) from pg_advisory_xact_lock(1263489101)", nativeQuery = true)
    long bloquearCatalogo();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);
}
