package com.komainos.inventario.repository;

import com.komainos.inventario.model.ConfiguracionGrupo;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.ModalidadPlanificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ConfiguracionGrupoRepositorio extends JpaRepository<ConfiguracionGrupo, Integer> {

    Optional<ConfiguracionGrupo> findByGrupoId(Integer idGrupo);

    List<ConfiguracionGrupo> findByModalidadPlanificacion(ModalidadPlanificacion modalidad);

    @Query("""
            select new com.komainos.inventario.repository.ConteoPorCuenta(c.idCuentaServicio, count(c))
            from ConfiguracionGrupo c where c.idCuentaServicio is not null group by c.idCuentaServicio""")
    List<ConteoPorCuenta> contarPorCuenta();

    /** La familia de un grupo es la de sus integrantes, que comparten sistema operativo (RF20) */
    @Query("""
            select distinct so.familia from ConfiguracionGrupo c
            join c.grupo g join g.integrantes i join i.servidor s
            join s.versionSistemaOperativo v join v.sistemaOperativo so
            where c.idCuentaServicio = :idCuenta or (:incluirSinCuenta = true and c.idCuentaServicio is null)""")
    Set<FamiliaSistemaOperativo> familiasQueUsan(@Param("idCuenta") Integer idCuenta,
                                                 @Param("incluirSinCuenta") boolean incluirSinCuenta);
}
