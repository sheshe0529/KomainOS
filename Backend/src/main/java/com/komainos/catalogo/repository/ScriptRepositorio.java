package com.komainos.catalogo.repository;
import com.komainos.catalogo.model.Script;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.*;
public interface ScriptRepositorio extends JpaRepository<Script,Integer> {
 @Query(value="select count(*) from pg_advisory_xact_lock(1263489101)",nativeQuery=true) long bloquearCatalogo(); @EntityGraph(attributePaths={"implementaciones"}) Page<Script> findAll(Pageable p);
 boolean existsByNombreIgnoreCaseAndIdNot(String nombre,Integer id);
 boolean existsByNombreIgnoreCase(String nombre); }
