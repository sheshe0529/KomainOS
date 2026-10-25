package com.komainos.catalogo.repository;
import com.komainos.catalogo.model.ImplementacionScript;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.*;
public interface ImplementacionScriptRepositorio extends JpaRepository<ImplementacionScript,Integer> { @EntityGraph(attributePaths={"script","versionSistemaOperativo.sistemaOperativo","versiones"}) Optional<ImplementacionScript> findConDetalleById(Integer id);
 List<ImplementacionScript> findByVersionSistemaOperativoId(Integer id); }
