package com.komainos.inventario.repository;

import com.komainos.inventario.model.NivelCriticidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NivelCriticidadRepositorio extends JpaRepository<NivelCriticidad, Integer> {

    List<NivelCriticidad> findAllByOrderByPrioridadAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);

    boolean existsByPrioridad(Integer prioridad);

    boolean existsByPrioridadAndIdNot(Integer prioridad, Integer id);

    /** Las órdenes también lo referencian con RESTRICT: se consulta para dar un mensaje claro (HU11 CA3) */
    @Query(value = """
            select exists (select 1 from servidor where id_nivel_criticidad = :id)
                or exists (select 1 from orden where id_nivel_criticidad = :id)
            """, nativeQuery = true)
    boolean estaEnUso(@Param("id") Integer id);
}
