package com.komainos.inventario.repository;

import com.komainos.inventario.model.ListaReferencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ListaReferenciaRepositorio extends JpaRepository<ListaReferencia, Integer> {

    List<ListaReferencia> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);

    /** fk_evaluacion_umbral_lista es RESTRICT: una lista ya aplicada en una evaluación no se elimina */
    @Query(value = "select exists (select 1 from \"KomainOS\".evaluacion_umbral where id_lista_referencia_aplicada = :id)",
            nativeQuery = true)
    boolean tieneEvaluaciones(@Param("id") Integer idLista);
}
