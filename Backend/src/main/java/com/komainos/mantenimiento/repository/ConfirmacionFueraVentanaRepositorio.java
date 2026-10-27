package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.ConfirmacionFueraVentana;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfirmacionFueraVentanaRepositorio extends JpaRepository<ConfirmacionFueraVentana, Integer> {

    boolean existsByOrdenId(Integer idOrden);
}
