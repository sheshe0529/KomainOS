package com.komainos.inventario.repository;

import com.komainos.inventario.model.UmbralExcepcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UmbralExcepcionRepositorio extends JpaRepository<UmbralExcepcion, Integer> {

    /** Carga de una vez el objetivo y la lista de cada excepción del umbral */
    @Query("""
            select e from UmbralExcepcion e
              left join fetch e.servidor
              left join fetch e.grupo
              left join fetch e.entorno
              left join fetch e.nivelCriticidad
              left join fetch e.lista
            where e.umbral.id = :idUmbral
            """)
    List<UmbralExcepcion> findDeUmbral(@Param("idUmbral") Integer idUmbral);

    @Query("select e from UmbralExcepcion e join fetch e.umbral where e.lista.id = :idLista")
    List<UmbralExcepcion> findDeLista(@Param("idLista") Integer idLista);

    @Query("""
            select new com.komainos.inventario.repository.ConteoPorId(e.lista.id, count(e))
            from UmbralExcepcion e where e.lista is not null group by e.lista.id
            """)
    List<ConteoPorId> contarPorLista();

    /** fk_evaluacion_umbral_excepcion es RESTRICT: una excepción ya aplicada se desactiva, no se elimina */
    @Query(value = "select exists (select 1 from \"KomainOS\".evaluacion_umbral where id_umbral_excepcion = :id)",
            nativeQuery = true)
    boolean tieneEvaluaciones(@Param("id") Integer idExcepcion);
}
