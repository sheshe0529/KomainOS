package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.NotificacionRegla;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NotificacionReglaRepositorio extends JpaRepository<NotificacionRegla, Integer> {

    List<NotificacionRegla> findByIdRegla(Integer idRegla);

    List<NotificacionRegla> findByIdReglaIn(Collection<Integer> idsRegla);

    void deleteByIdRegla(Integer idRegla);
}
