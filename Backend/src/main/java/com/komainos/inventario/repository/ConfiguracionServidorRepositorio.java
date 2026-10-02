package com.komainos.inventario.repository;

import com.komainos.inventario.model.ConfiguracionServidor;
import com.komainos.inventario.model.FamiliaSistemaOperativo;
import com.komainos.inventario.model.ModalidadPlanificacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ConfiguracionServidorRepositorio extends JpaRepository<ConfiguracionServidor, Integer> {

    Optional<ConfiguracionServidor> findByServidorId(Integer idServidor);

    @EntityGraph(attributePaths = {"servidor.nivelCriticidad", "servidor.ventanas", "servidor.responsable"})
    List<ConfiguracionServidor> findByModalidadPlanificacion(ModalidadPlanificacion modalidad);

    @EntityGraph(attributePaths = {"servidor.versionSistemaOperativo.sistemaOperativo"})
    List<ConfiguracionServidor> findByServidorIdIn(Collection<Integer> idsServidores);

    @EntityGraph(attributePaths = {"servidor.versionSistemaOperativo.sistemaOperativo"})
    List<ConfiguracionServidor> findAllByOrderByServidorHostnameAsc();

    @Query("""
            select new com.komainos.inventario.repository.ConteoPorCuenta(c.idCuentaServicio, count(c))
            from ConfiguracionServidor c where c.idCuentaServicio is not null group by c.idCuentaServicio""")
    List<ConteoPorCuenta> contarPorCuenta();

    /** Con incluirSinCuenta también cuentan los que usan la predeterminada */
    @Query("""
            select distinct so.familia from ConfiguracionServidor c
            join c.servidor s join s.versionSistemaOperativo v join v.sistemaOperativo so
            where c.idCuentaServicio = :idCuenta or (:incluirSinCuenta = true and c.idCuentaServicio is null)""")
    Set<FamiliaSistemaOperativo> familiasQueUsan(@Param("idCuenta") Integer idCuenta,
                                                 @Param("incluirSinCuenta") boolean incluirSinCuenta);
}
