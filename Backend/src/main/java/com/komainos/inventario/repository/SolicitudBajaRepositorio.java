package com.komainos.inventario.repository;

import com.komainos.inventario.model.EstadoSolicitudBaja;
import com.komainos.inventario.model.SolicitudBaja;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SolicitudBajaRepositorio extends JpaRepository<SolicitudBaja, Integer> {

    @EntityGraph(attributePaths = "solicitante")
    Optional<SolicitudBaja> findFirstByServidorIdAndEstado(Integer idServidor, EstadoSolicitudBaja estado);

    @EntityGraph(attributePaths = "solicitante")
    List<SolicitudBaja> findByServidorIdOrderByFechaSolicitudDesc(Integer idServidor);
}
