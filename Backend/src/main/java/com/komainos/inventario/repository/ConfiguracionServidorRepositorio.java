package com.komainos.inventario.repository;

import com.komainos.inventario.model.ConfiguracionServidor;
import com.komainos.inventario.model.ModalidadPlanificacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConfiguracionServidorRepositorio extends JpaRepository<ConfiguracionServidor, Integer> {

    Optional<ConfiguracionServidor> findByServidorId(Integer idServidor);

    /** Servidores activos en una modalidad, con lo que la planificacion necesita. */
    @EntityGraph(attributePaths = {"servidor.nivelCriticidad", "servidor.ventanas", "servidor.responsable"})
    List<ConfiguracionServidor> findByModalidadPlanificacion(ModalidadPlanificacion modalidad);
}
