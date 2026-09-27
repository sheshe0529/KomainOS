package com.komainos.inventario.infra;

import com.komainos.inventario.dominio.ConfiguracionGrupo;
import com.komainos.inventario.dominio.ModalidadPlanificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfiguracionGrupoRepositorio extends JpaRepository<ConfiguracionGrupo, Integer> {

    Optional<ConfiguracionGrupo> findByGrupoId(Integer idGrupo);

    List<ConfiguracionGrupo> findByModalidadPlanificacion(ModalidadPlanificacion modalidad);
}
