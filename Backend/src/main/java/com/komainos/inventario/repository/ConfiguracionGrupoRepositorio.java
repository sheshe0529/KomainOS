package com.komainos.inventario.repository;

import com.komainos.inventario.model.ConfiguracionGrupo;
import com.komainos.inventario.model.EstadoGrupo;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.ModalidadPlanificacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ConfiguracionGrupoRepositorio extends JpaRepository<ConfiguracionGrupo, Integer> {

    Optional<ConfiguracionGrupo> findByGrupoId(Integer idGrupo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConfiguracionGrupo c where c.grupo.id = :idGrupo")
    Optional<ConfiguracionGrupo> findParaRevision(@Param("idGrupo") Integer idGrupo);

    List<ConfiguracionGrupo> findByRevisionPeriodicaHabilitadaTrueAndGrupoEstadoOrderByGrupoNombreAsc(EstadoGrupo estado);

    List<ConfiguracionGrupo> findByGrupoIdIn(Collection<Integer> idsGrupos);

    List<ConfiguracionGrupo> findByModalidadPlanificacion(ModalidadPlanificacion modalidad);

    @Query("""
            select new com.komainos.inventario.repository.CuentasConfiguradas(c.idCuentaServicioEvaluacion, c.idCuentaServicioMantenimiento)
            from ConfiguracionGrupo c""")
    List<CuentasConfiguradas> cuentasConfiguradas();

    /** La familia de un grupo es la de sus integrantes, que comparten sistema operativo (RF20) */
    @Query("""
            select distinct so.familia from ConfiguracionGrupo c
            join c.grupo g join g.integrantes i join i.servidor s
            join s.versionSistemaOperativo v join v.sistemaOperativo so
            where (:evaluacion = true and (c.idCuentaServicioEvaluacion = :idCuenta
                    or (:incluirSinCuenta = true and c.idCuentaServicioEvaluacion is null)))
               or (:evaluacion = false and (c.idCuentaServicioMantenimiento = :idCuenta
                    or (:incluirSinCuenta = true and c.idCuentaServicioMantenimiento is null)))""")
    Set<FamiliaSistemaOperativo> familiasQueUsan(@Param("idCuenta") Integer idCuenta,
                                                 @Param("incluirSinCuenta") boolean incluirSinCuenta,
                                                 @Param("evaluacion") boolean evaluacion);
}
