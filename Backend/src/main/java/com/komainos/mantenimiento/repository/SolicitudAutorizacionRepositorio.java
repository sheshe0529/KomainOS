package com.komainos.mantenimiento.repository;

import com.komainos.mantenimiento.model.SolicitudAutorizacion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitudAutorizacionRepositorio extends JpaRepository<SolicitudAutorizacion, Integer> {

    /** La ronda vigente: las anteriores quedan como historial (RF48) */
    @EntityGraph(attributePaths = {"regla.entorno"})
    Optional<SolicitudAutorizacion> findFirstByOrdenIdOrderByNumeroRondaDesc(Integer idOrden);

    @EntityGraph(attributePaths = {"regla.entorno"})
    List<SolicitudAutorizacion> findByOrdenIdOrderByNumeroRondaAsc(Integer idOrden);
}
