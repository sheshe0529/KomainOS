package com.komainos.planificacion.infra;

import com.komainos.planificacion.dominio.FactorCiclo;
import com.komainos.planificacion.dominio.ResultadoCiclo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FactorCicloRepositorio extends JpaRepository<FactorCiclo, Integer> {

    Optional<FactorCiclo> findByResultado(ResultadoCiclo resultado);
}
