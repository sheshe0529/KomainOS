package com.komainos.catalogo.repository;

import com.komainos.catalogo.model.Tarea;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TareaRepositorio extends JpaRepository<Tarea, Integer> {

    @Override
    @EntityGraph(attributePaths = {"script"})
    Page<Tarea> findAll(Pageable pagina);

    /** Buscador del listado: el patrón (en minúsculas, con sus comodines) en el nombre o la descripción de la tarea o en el nombre de su script */
    @EntityGraph(attributePaths = {"script"})
    @Query("""
            select t from Tarea t
            where lower(t.nombre) like :patron
               or lower(coalesce(t.descripcion, '')) like :patron
               or lower(t.script.nombre) like :patron
            """)
    Page<Tarea> buscar(@Param("patron") String patron, Pageable pagina);

    /** RF41: cada tarea una sola vez aunque la activen varios umbrales, ordenadas por prioridad */
    @EntityGraph(attributePaths = {"script"})
    @Query("""
            select distinct t from Tarea t join t.idsUmbrales u
            where t.activo = true and u in :umbrales
            order by t.prioridad, t.id
            """)
    List<Tarea> findActivadas(@Param("umbrales") Collection<Integer> idsUmbrales);

    @EntityGraph(attributePaths = {"script"})
    List<Tarea> findByActivoTrue();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);

    long countByScriptId(Integer idScript);
}
