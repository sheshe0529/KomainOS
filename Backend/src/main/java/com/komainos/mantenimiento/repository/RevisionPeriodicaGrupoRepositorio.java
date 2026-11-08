package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.RevisionPeriodicaGrupo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RevisionPeriodicaGrupoRepositorio extends JpaRepository<RevisionPeriodicaGrupo, Integer> {

    Optional<RevisionPeriodicaGrupo> findFirstByGrupoIdOrderByFechaInicioDesc(Integer idGrupo);
}
