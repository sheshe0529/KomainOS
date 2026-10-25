package com.komainos.catalogo.repository;
import com.komainos.catalogo.model.Tarea;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.*;
import java.util.*;
public interface TareaRepositorio extends JpaRepository<Tarea,Integer> { @EntityGraph(attributePaths={"script","idsUmbrales"}) Page<Tarea> findAll(Pageable p);
 @Query("select distinct t from Tarea t join t.idsUmbrales u where t.activo=true and u in :ids order by t.prioridad,t.id") List<Tarea> findActivadas(@Param("ids") Collection<Integer> ids); }
