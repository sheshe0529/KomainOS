package com.komainos.alerta.repository;

import com.komainos.alerta.model.Alerta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertaRepositorio extends JpaRepository<Alerta, Integer> {
}
