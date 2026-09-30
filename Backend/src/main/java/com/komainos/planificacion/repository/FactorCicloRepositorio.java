package com.komainos.planificacion.repository;

import com.komainos.planificacion.model.FactorCiclo;
import com.komainos.planificacion.model.ResultadoCiclo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FactorCicloRepositorio extends JpaRepository<FactorCiclo, Integer> {

    Optional<FactorCiclo> findByResultado(ResultadoCiclo resultado);
}
