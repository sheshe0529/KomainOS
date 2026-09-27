package com.komainos.inventario.infra;

import com.komainos.inventario.dominio.Entorno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntornoRepositorio extends JpaRepository<Entorno, Integer> {

    List<Entorno> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);
}
