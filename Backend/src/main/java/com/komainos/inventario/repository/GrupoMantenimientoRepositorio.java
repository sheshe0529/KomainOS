package com.komainos.inventario.repository;

import com.komainos.inventario.model.GrupoMantenimiento;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GrupoMantenimientoRepositorio extends JpaRepository<GrupoMantenimiento, Integer> {

    @EntityGraph(attributePaths = {"integrantes.servidor.nivelCriticidad", "integrantes.servidor.ventanas",
            "integrantes.servidor.entorno", "integrantes.servidor.responsable",
            "integrantes.servidor.versionSistemaOperativo.sistemaOperativo"})
    Optional<GrupoMantenimiento> findConIntegrantesById(Integer id);

    @EntityGraph(attributePaths = {"integrantes.servidor.nivelCriticidad", "integrantes.servidor.entorno",
            "integrantes.servidor.responsable", "integrantes.servidor.versionSistemaOperativo.sistemaOperativo"})
    List<GrupoMantenimiento> findAllByOrderByNombreAsc();

    /** El alcance del responsable en la consulta: los grupos con al menos un integrante a su cargo */
    @EntityGraph(attributePaths = {"integrantes.servidor.nivelCriticidad", "integrantes.servidor.entorno",
            "integrantes.servidor.responsable", "integrantes.servidor.versionSistemaOperativo.sistemaOperativo"})
    @Query("""
            select g from GrupoMantenimiento g
            where exists (select 1 from GrupoServidor i where i.grupo = g and i.servidor.responsable.id = :idResponsable)
            order by g.nombre""")
    List<GrupoMantenimiento> findVisiblesPara(@Param("idResponsable") Integer idResponsable);

    @Query("select distinct g from GrupoMantenimiento g join g.integrantes i where i.servidor.id = :idServidor order by g.nombre")
    List<GrupoMantenimiento> findDelServidor(@Param("idServidor") Integer idServidor);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);
}
