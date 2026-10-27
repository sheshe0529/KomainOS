package com.komainos.inventario.repository;

import com.komainos.inventario.model.Umbral;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UmbralRepositorio extends JpaRepository<Umbral, Integer> {

    @EntityGraph(attributePaths = {"metrica", "lista"})
    @Query("select u from Umbral u order by u.metrica.nombre, u.nombre")
    List<Umbral> findTodos();

    @EntityGraph(attributePaths = {"metrica", "lista"})
    @Query("select u from Umbral u where u.metrica.id = :idMetrica order by u.nombre")
    List<Umbral> findDeMetrica(@Param("idMetrica") Integer idMetrica);

    @EntityGraph(attributePaths = {"metrica", "lista"})
    Optional<Umbral> findConMetricaById(Integer id);

    /** Los que se evalúan: umbral activo de una métrica activa */
    @EntityGraph(attributePaths = {"metrica", "lista"})
    @Query("select u from Umbral u where u.activo = true and u.metrica.activo = true order by u.metrica.nombre, u.nombre")
    List<Umbral> findAplicables();

    long countByIdIn(Collection<Integer> ids);

    boolean existsByMetricaIdAndNombreIgnoreCase(Integer idMetrica, String nombre);

    boolean existsByMetricaIdAndNombreIgnoreCaseAndIdNot(Integer idMetrica, String nombre, Integer id);

    long countByMetricaId(Integer idMetrica);

    List<Umbral> findByListaIdOrderByNombreAsc(Integer idLista);

    @Query("""
            select new com.komainos.inventario.repository.ConteoPorId(u.lista.id, count(u))
            from Umbral u where u.lista is not null group by u.lista.id
            """)
    List<ConteoPorId> contarPorLista();

    /** fk_evaluacion_umbral_umbral es RESTRICT: un umbral ya evaluado se desactiva, no se elimina */
    @Query(value = "select exists (select 1 from \"KomainOS\".evaluacion_umbral where id_umbral = :id)", nativeQuery = true)
    boolean tieneEvaluaciones(@Param("id") Integer idUmbral);
}
