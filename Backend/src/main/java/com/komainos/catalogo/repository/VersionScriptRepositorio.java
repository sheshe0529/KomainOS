package com.komainos.catalogo.repository;
import com.komainos.catalogo.model.VersionScript;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.*;
public interface VersionScriptRepositorio extends JpaRepository<VersionScript,Integer> { @EntityGraph(attributePaths={"implementacion.script","implementacion.versionSistemaOperativo.sistemaOperativo","parametros"}) Optional<VersionScript> findConDetalleById(Integer id);
 @Query("select max(v.numeroVersion) from VersionScript v where v.implementacion.id=:id") Integer ultimoNumero(@Param("id") Integer id); }
