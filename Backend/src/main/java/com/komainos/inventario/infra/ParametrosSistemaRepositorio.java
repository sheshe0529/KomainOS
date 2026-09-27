package com.komainos.inventario.infra;

import com.komainos.inventario.dominio.ParametrosSistema;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametrosSistemaRepositorio extends JpaRepository<ParametrosSistema, Integer> {
}
