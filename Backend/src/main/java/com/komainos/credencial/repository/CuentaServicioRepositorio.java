package com.komainos.credencial.repository;

import com.komainos.credencial.model.CuentaServicio;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CuentaServicioRepositorio extends JpaRepository<CuentaServicio, Integer> {

    @EntityGraph(attributePaths = {"versiones"})
    List<CuentaServicio> findAllByOrderByEstadoAscNombreAsc();

    @EntityGraph(attributePaths = {"versiones"})
    Optional<CuentaServicio> findConVersionesById(Integer id);

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);
}
